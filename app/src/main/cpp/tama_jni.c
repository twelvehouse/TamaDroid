/*
 * TamaDroid JNI bridge + HAL implementation.
 *
 * Architecture mirrors the StefanBauwens Pebble TamaLib port (see README Credits)
 * and a verified headless TamaLib runner:
 *
 *   - A continuous step loop (nativeRun) drives tamalib_step() with the HAL clock
 *     pacing to real time (speed 1: get_timestamp = real monotonic, sleep_until
 *     really sleeps). 1 Tamagotchi second == 1 real second.
 *   - Buttons are applied IMMEDIATELY: the UI thread writes g_btn_want, the loop
 *     applies any change to hw_set_button every step (sub-ms latency, like Pebble's
 *     click handlers). No polling/latch.
 *   - Sound is event-driven: the HAL play_frequency callback upcalls Java
 *     (NativeBridge.onBuzzer) on each on/off edge, exactly like Pebble calls
 *     speaker_play_tone()/speaker_stop(). Never missed by frame timing.
 *   - Frames are snapshotted under a mutex at ~30 fps; the UI reads them via
 *     nativeGetFrame from a separate thread.
 *   - Saves: the loop serializes state and upcalls NativeBridge.onSave periodically,
 *     on request (nativeRequestSave), and once more on stop.
 *
 * TamaLib is single-threaded by design (global mutable state); ALL TamaLib access
 * happens on the loop thread inside nativeRun (+ nativeInit/Restore/CatchUp called
 * before the loop starts). nativeSetButton/RequestSave/Stop only set flags and are
 * safe from any thread. nativeGetFrame reads a mutex-protected snapshot.
 *
 * GPLv2 (links TamaLib).
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>
#include <android/log.h>

#include "tamalib/tamalib.h"

#define LOG_TAG "TamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define TS_FREQ      32768u            /* timestamp granularity (1/32768 s)   */
#define ROM_WORDS    6144              /* P1/P2 program length                */
#define ROM_PADDED   8192              /* PC is 13-bit -> pad to avoid OOB    */
#define FRAME_FPS    30
#define AUTOSAVE_SEC 30

/* E0C6S46 program counter is 13-bit -> pad to 8192 to avoid any OOB read. */
static u12_t   g_rom[ROM_PADDED];
static uint8_t g_fb[LCD_HEIGHT][LCD_WIDTH];   /* live framebuffer (loop thread) */
static uint8_t g_icons[ICON_NUM];
static int     g_initialized = 0;

/* Live snapshot, published at ~FRAME_FPS — read by the in-app UI (full animation; a
 * brief mid-redraw tear self-corrects within one frame, which is fine in-app). */
static uint8_t        g_snap_fb[LCD_HEIGHT][LCD_WIDTH];
static uint8_t        g_snap_icons[ICON_NUM];
static pthread_mutex_t g_snap_lock = PTHREAD_MUTEX_INITIALIZER;

/* Settle snapshot, published only once the display has been quiet for SETTLE — read by
 * the WIDGET when anti-tearing is on. Never tears, but a 30 Hz animation (which never
 * settles) is skipped; acceptable for the non-interactive widget. */
static uint8_t        g_settle_fb[LCD_HEIGHT][LCD_WIDTH];
static uint8_t        g_settle_icons[ICON_NUM];
static pthread_mutex_t g_settle_lock = PTHREAD_MUTEX_INITIALIZER;

/* Frame-settle publish. Measured: the firmware redraws the LCD in ~2 ms bursts ~32x/s,
 * with ~28 ms of quiet between bursts, and NO consistent frame-end pixel. Sampling
 * mid-burst tears. So publish the snapshot only once display writes have been quiet for
 * SETTLE (long enough to bridge a multi-refresh pose update): the published frame is then
 * always a complete pose, never partial. Used for the widget's anti-tearing mode. */
static volatile int         g_fb_dirty      = 0;
static volatile timestamp_t g_last_write_ts = 0;
/* A single firmware pose update is written across SEVERAL 32 Hz refreshes (~31 ms apart),
 * so the settle window must bridge those sub-updates (> the ~29 ms inter-refresh quiet) and
 * only publish once the whole new pose is drawn and stable. Too small -> partial frames
 * (top-left new, rest old); too large -> visible lag / could starve fast animation. */
#define SETTLE_MS     64
#define SETTLE_TICKS  ((timestamp_t)(TS_FREQ * SETTLE_MS / 1000))
static timestamp_t now_ts(void);   /* forward decl (defined below) */
static void mark_write(void) { g_last_write_ts = now_ts(); g_fb_dirty = 1; }



/* Control flags (written from UI thread, read by the loop). */
static volatile int g_running      = 0;
static volatile int g_btn_want[3]  = { 0, 0, 0 };   /* desired level   */
static volatile int g_btn_edge[3]  = { 0, 0, 0 };   /* press latch     */
static volatile int g_save_request = 0;

/* In-game clock overwrite request: the UI/ticker fills the phone time and raises the flag; the
 * loop writes it to the clock RAM on its own thread next step (instant). */
static volatile int g_clock_request = 0;
static volatile int g_clock_h = 0, g_clock_m = 0, g_clock_s = 0;

/* Minimum time a button stays pressed once tapped, so the firmware's periodic key
 * scan always catches it (a sub-scan touchscreen tap would otherwise be missed). */
#define BTN_MIN_HOLD_TICKS  ((timestamp_t)(TS_FREQ * 120 / 1000))   /* ~120 ms */

/* Buzzer (deci-Hertz); suppressed while fast-forwarding. The emulator writes the
 * disable bit almost every cycle, so we upcall only on real state/frequency changes. */
static uint32_t     g_snd_dhz        = 0;
static int          g_snd_on         = 0;
static uint32_t     g_snd_last_dhz   = 0;
static volatile int g_suppress_sound = 0;

/* Cached JNI handles for upcalls into com.tamadroid.core.NativeBridge. */
static JavaVM *  g_vm        = NULL;
static jclass    g_bridge    = NULL;
static jmethodID g_m_buzzer  = NULL;   /* onBuzzer(int hz, boolean on) */
static jmethodID g_m_save    = NULL;   /* onSave(byte[] state)         */

/* ---- real monotonic clock, expressed in 1/32768 s ticks ---- */
static struct timespec g_t0;

static timestamp_t now_ts(void)
{
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    int64_t ns = (int64_t)(t.tv_sec - g_t0.tv_sec) * 1000000000LL
               + (int64_t)(t.tv_nsec - g_t0.tv_nsec);
    return (timestamp_t)((ns * (int64_t)TS_FREQ) / 1000000000LL);
}

static void reset_clock(void) { clock_gettime(CLOCK_MONOTONIC, &g_t0); }

/* ---- JNI upcalls (loop thread is a Java thread, so GetEnv works) ---- */
static JNIEnv *get_env(void)
{
    JNIEnv *e = NULL;
    if (!g_vm) return NULL;
    if ((*g_vm)->GetEnv(g_vm, (void **)&e, JNI_VERSION_1_6) != JNI_OK) return NULL;
    return e;
}

static void emit_buzzer(uint32_t dhz, int on)
{
    if (g_suppress_sound) return;
    if (!g_bridge || !g_m_buzzer) return;
    JNIEnv *e = get_env();
    if (!e) return;
    (*e)->CallStaticVoidMethod(e, g_bridge, g_m_buzzer,
                               (jint)(dhz / 10), on ? JNI_TRUE : JNI_FALSE);
}

static void emit_save(void)
{
    if (!g_bridge || !g_m_save) return;
    JNIEnv *e = get_env();
    if (!e) return;
    flat_state_t st = cpu_get_flat_state();
    jbyteArray arr = (*e)->NewByteArray(e, (jsize)sizeof(st));
    if (!arr) return;
    (*e)->SetByteArrayRegion(e, arr, 0, (jsize)sizeof(st), (const jbyte *)&st);
    (*e)->CallStaticVoidMethod(e, g_bridge, g_m_save, arr);
    (*e)->DeleteLocalRef(e, arr);
}

/* ---------------- HAL ---------------- */
static void *      h_malloc(u32_t s)                  { return malloc(s); }
static void        h_free(void *p)                    { free(p); }
static void        h_halt(void)                       { }
static bool_t      h_log_enabled(log_level_t l)       { (void)l; return 0; }
static void        h_log(log_level_t l, char *b, ...) { (void)l; (void)b; }
static timestamp_t h_get_timestamp(void)              { return now_ts(); }

static void h_sleep_until(timestamp_t ts)
{
    for (;;) {
        int32_t diff = (int32_t)(ts - now_ts());
        if (diff <= 0) return;
        uint32_t us = (uint32_t)diff * 1000000UL / TS_FREQ;
        if (us == 0) return;
        struct timespec req;
        req.tv_sec  = us / 1000000UL;
        req.tv_nsec = (long)(us % 1000000UL) * 1000L;
        nanosleep(&req, NULL);
    }
}

static void h_set_matrix(u8_t x, u8_t y, bool_t v)
{
    if (x < LCD_WIDTH && y < LCD_HEIGHT && g_fb[y][x] != (uint8_t)v) {
        g_fb[y][x] = (uint8_t)v;
        mark_write();
    }
}
static void h_set_icon(u8_t i, bool_t v)
{
    if (i < ICON_NUM && g_icons[i] != (uint8_t)v) {
        g_icons[i] = (uint8_t)v;
        mark_write();
    }
}
static void h_update_screen(void)                  { }
static void h_set_frequency(u32_t f)               { g_snd_dhz = f; }
static void h_play_frequency(bool_t e)
{
    int on = e ? 1 : 0;
    /* Only act on a real change (on/off transition, or a new tone while on). */
    if (on == g_snd_on && (!on || g_snd_dhz == g_snd_last_dhz)) return;
    g_snd_on = on;
    g_snd_last_dhz = g_snd_dhz;
    emit_buzzer(g_snd_dhz, on);
}
static int  h_handler(void)                        { return 0; }

static hal_t g_hal_impl = {
    .malloc         = h_malloc,
    .free           = h_free,
    .halt           = h_halt,
    .is_log_enabled = h_log_enabled,
    .log            = h_log,
    .sleep_until    = h_sleep_until,
    .get_timestamp  = h_get_timestamp,
    .update_screen  = h_update_screen,
    .set_lcd_matrix = h_set_matrix,
    .set_lcd_icon   = h_set_icon,
    .set_frequency  = h_set_frequency,
    .play_frequency = h_play_frequency,
    .handler        = h_handler,
};

/* ---------------- JNI ---------------- */
#define JNI(ret, name) JNIEXPORT ret JNICALL Java_com_tamadroid_core_TamaCore_##name

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    g_vm = vm;
    return JNI_VERSION_1_6;
}

static void cache_bridge(JNIEnv *env)
{
    if (g_bridge) return;
    jclass c = (*env)->FindClass(env, "com/tamadroid/core/NativeBridge");
    if (!c) return;
    g_bridge   = (jclass)(*env)->NewGlobalRef(env, c);
    g_m_buzzer = (*env)->GetStaticMethodID(env, g_bridge, "onBuzzer", "(IZ)V");
    g_m_save   = (*env)->GetStaticMethodID(env, g_bridge, "onSave", "([B)V");
}

JNI(jboolean, nativeInit)(JNIEnv *env, jclass clazz, jintArray rom)
{
    (void)clazz;
    cache_bridge(env);

    if (g_initialized) { tamalib_release(); g_initialized = 0; }

    jsize n = (*env)->GetArrayLength(env, rom);
    if (n != ROM_WORDS) {
        LOGE("nativeInit: expected %d ROM words, got %d", ROM_WORDS, (int)n);
        return JNI_FALSE;
    }
    jint *src = (*env)->GetIntArrayElements(env, rom, NULL);
    if (!src) return JNI_FALSE;
    memset(g_rom, 0, sizeof(g_rom));
    for (jsize i = 0; i < n; i++) g_rom[i] = (u12_t)(src[i] & 0x0FFF);
    (*env)->ReleaseIntArrayElements(env, rom, src, JNI_ABORT);

    memset(g_fb, 0, sizeof(g_fb));
    memset(g_icons, 0, sizeof(g_icons));
    g_btn_want[0] = g_btn_want[1] = g_btn_want[2] = 0;
    g_btn_edge[0] = g_btn_edge[1] = g_btn_edge[2] = 0;

    tamalib_register_hal(&g_hal_impl);
    reset_clock();
    if (tamalib_init(g_rom, NULL, TS_FREQ)) {
        LOGE("tamalib_init failed");
        return JNI_FALSE;
    }
    tamalib_set_exec_mode(EXEC_MODE_RUN);
    tamalib_set_speed(1);
    g_initialized = 1;
    LOGI("tamalib initialised (rom[0]=0x%03X)", g_rom[0]);
    return JNI_TRUE;
}

JNI(jboolean, nativeRestore)(JNIEnv *env, jclass clazz, jbyteArray data)
{
    (void)clazz;
    if (!g_initialized || !data) return JNI_FALSE;
    jsize n = (*env)->GetArrayLength(env, data);
    if (n != (jsize)sizeof(flat_state_t)) {
        LOGE("nativeRestore: size mismatch (%d vs %d)", (int)n, (int)sizeof(flat_state_t));
        return JNI_FALSE;
    }
    flat_state_t st;
    (*env)->GetByteArrayRegion(env, data, 0, n, (jbyte *)&st);
    cpu_set_state(&st);
    tamalib_refresh_hw();
    reset_clock();
    return JNI_TRUE;
}

/* Write the in-game clock (HH:MM:SS) directly. Map (StefanBauwens/Watchy P1): seconds & minutes
 * are BCD nibble pairs (units, tens) at 0x10-0x13; the hour is a binary value split into low/high
 * nibbles at 0x14/0x15. Loop thread (TamaLib is single-threaded). */
static void write_game_clock(int h, int m, int s)
{
    if (!g_initialized || h < 0 || h > 23 || m < 0 || m > 59 || s < 0 || s > 59) return;
    state_t *st = tamalib_get_state();
    MEM_BUFFER_TYPE *mem = st->memory;
    SET_MEMORY(mem, 0x10, (MEM_BUFFER_TYPE)(s % 10));
    SET_MEMORY(mem, 0x11, (MEM_BUFFER_TYPE)(s / 10));
    SET_MEMORY(mem, 0x12, (MEM_BUFFER_TYPE)(m % 10));
    SET_MEMORY(mem, 0x13, (MEM_BUFFER_TYPE)(m / 10));
    SET_MEMORY(mem, 0x14, (MEM_BUFFER_TYPE)(h & 0x0F));
    SET_MEMORY(mem, 0x15, (MEM_BUFFER_TYPE)((h >> 4) & 0x0F));
    LOGI("clock: in-game clock set to %02d:%02d:%02d", h, m, s);
}

/* Clock-overwrite (launch): set the in-game clock to the phone time. Called before nativeRun. */
JNI(void, nativeSyncClock)(JNIEnv *env, jclass clazz, jint h, jint m, jint s)
{
    (void)env; (void)clazz;
    write_game_clock((int)h, (int)m, (int)s);
}

/* Request the running loop to set the in-game clock (toggle / periodic). The loop applies it on
 * its own thread next step. Any thread. */
JNI(void, nativeRequestClockSync)(JNIEnv *env, jclass clazz, jint h, jint m, jint s)
{
    (void)env; (void)clazz;
    g_clock_h = (int)h; g_clock_m = (int)m; g_clock_s = (int)s;
    g_clock_request = 1;
}

/* Blocking real-time step loop. Returns when nativeStop sets g_running = 0. */
JNI(void, nativeRun)(JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    if (!g_initialized) return;

    int applied[3] = { 0, 0, 0 };
    timestamp_t hold_until[3] = { 0, 0, 0 };
    reset_clock();
    timestamp_t next_frame = 0;
    timestamp_t next_save  = (timestamp_t)AUTOSAVE_SEC * TS_FREQ;

    /* Seed both snapshots with the current (coherent) frame. */
    pthread_mutex_lock(&g_snap_lock);
    memcpy(g_snap_fb, g_fb, sizeof(g_fb));
    memcpy(g_snap_icons, g_icons, sizeof(g_icons));
    pthread_mutex_unlock(&g_snap_lock);
    pthread_mutex_lock(&g_settle_lock);
    memcpy(g_settle_fb, g_fb, sizeof(g_fb));
    memcpy(g_settle_icons, g_icons, sizeof(g_icons));
    pthread_mutex_unlock(&g_settle_lock);

    g_running = 1;
    while (g_running) {
        /* Apply a pending clock-overwrite (instant, on the loop thread). */
        if (g_clock_request) {
            g_clock_request = 0;
            write_game_clock(g_clock_h, g_clock_m, g_clock_s);
        }

        timestamp_t now = now_ts();

        /* Apply buttons: latch the press edge (never miss a quick tap) and hold for
         * a minimum so the firmware's key scan registers it (Pebble-style press). */
        for (int b = 0; b < 3; b++) {
            int want = g_btn_want[b];
            int edge = g_btn_edge[b];
            g_btn_edge[b] = 0;
            if (edge || want) {
                if (!applied[b]) {
                    hw_set_button((button_t)b, BTN_STATE_PRESSED);
                    applied[b] = 1;
                }
                hold_until[b] = now + BTN_MIN_HOLD_TICKS;
            } else if (applied[b] && (int32_t)(now - hold_until[b]) >= 0) {
                hw_set_button((button_t)b, BTN_STATE_RELEASED);
                applied[b] = 0;
            }
        }

        tamalib_step();                 /* HAL paces to real time (per-instruction sleep) */

        now = now_ts();

        /* Live snapshot @ ~FRAME_FPS for the in-app view (full animation; brief tears
         * self-correct next frame). */
        if ((int32_t)(now - next_frame) >= 0) {
            next_frame = now + TS_FREQ / FRAME_FPS;
            pthread_mutex_lock(&g_snap_lock);
            memcpy(g_snap_fb, g_fb, sizeof(g_fb));
            memcpy(g_snap_icons, g_icons, sizeof(g_icons));
            pthread_mutex_unlock(&g_snap_lock);
        }

        /* Settle snapshot for the widget's anti-tearing mode: publish only once writes
         * have been quiet for SETTLE_TICKS, so a multi-refresh pose update is committed
         * whole (never partial). A 30 Hz animation never settles, so it's skipped. */
        if (g_fb_dirty && (int32_t)(now - g_last_write_ts) >= (int32_t)SETTLE_TICKS) {
            g_fb_dirty = 0;
            pthread_mutex_lock(&g_settle_lock);
            memcpy(g_settle_fb, g_fb, sizeof(g_fb));
            memcpy(g_settle_icons, g_icons, sizeof(g_icons));
            pthread_mutex_unlock(&g_settle_lock);
        }

        if (g_save_request || (int32_t)(now - next_save) >= 0) {
            g_save_request = 0;
            next_save = now + (timestamp_t)AUTOSAVE_SEC * TS_FREQ;
            emit_save();
        }
    }

    emit_save();                        /* final save on stop */
}

JNI(void, nativeStop)(JNIEnv *env, jclass clazz)        { (void)env; (void)clazz; g_running = 0; }
JNI(void, nativeRequestSave)(JNIEnv *env, jclass clazz) { (void)env; (void)clazz; g_save_request = 1; }

/* Game-speed multiplier: 1 = real time, 2/3/5 = faster, 0 = as fast as possible. */
JNI(void, nativeSetSpeed)(JNIEnv *env, jclass clazz, jint speed)
{
    (void)env; (void)clazz;
    if (g_initialized) tamalib_set_speed((u8_t)speed);
}

JNI(void, nativeSetButton)(JNIEnv *env, jclass clazz, jint btn, jboolean pressed)
{
    (void)env; (void)clazz;
    if (btn < 0 || btn > 2) return;
    g_btn_want[btn] = pressed ? 1 : 0;
    if (pressed) g_btn_edge[btn] = 1;   /* latch so a tap shorter than a loop step still registers */
}

JNI(void, nativeGetFrame)(JNIEnv *env, jclass clazz, jbyteArray fbOut, jbyteArray iconsOut)
{
    (void)clazz;
    uint8_t fb[LCD_HEIGHT][LCD_WIDTH];
    uint8_t icons[ICON_NUM];
    pthread_mutex_lock(&g_snap_lock);
    memcpy(fb, g_snap_fb, sizeof(fb));
    memcpy(icons, g_snap_icons, sizeof(icons));
    pthread_mutex_unlock(&g_snap_lock);

    if (fbOut && (*env)->GetArrayLength(env, fbOut) >= (jsize)(LCD_WIDTH * LCD_HEIGHT))
        (*env)->SetByteArrayRegion(env, fbOut, 0, LCD_WIDTH * LCD_HEIGHT, (const jbyte *)fb);
    if (iconsOut && (*env)->GetArrayLength(env, iconsOut) >= ICON_NUM)
        (*env)->SetByteArrayRegion(env, iconsOut, 0, ICON_NUM, (const jbyte *)icons);
}

/* Settle frame for the widget's anti-tearing mode: the last fully-settled (never-torn)
 * frame. Skips 30 Hz animations (they never settle). */
JNI(void, nativeGetSettleFrame)(JNIEnv *env, jclass clazz, jbyteArray fbOut, jbyteArray iconsOut)
{
    (void)clazz;
    uint8_t fb[LCD_HEIGHT][LCD_WIDTH];
    uint8_t icons[ICON_NUM];
    pthread_mutex_lock(&g_settle_lock);
    memcpy(fb, g_settle_fb, sizeof(fb));
    memcpy(icons, g_settle_icons, sizeof(icons));
    pthread_mutex_unlock(&g_settle_lock);

    if (fbOut && (*env)->GetArrayLength(env, fbOut) >= (jsize)(LCD_WIDTH * LCD_HEIGHT))
        (*env)->SetByteArrayRegion(env, fbOut, 0, LCD_WIDTH * LCD_HEIGHT, (const jbyte *)fb);
    if (iconsOut && (*env)->GetArrayLength(env, iconsOut) >= ICON_NUM)
        (*env)->SetByteArrayRegion(env, iconsOut, 0, ICON_NUM, (const jbyte *)icons);
}

JNI(void, nativeRelease)(JNIEnv *env, jclass clazz)
{
    (void)env; (void)clazz;
    if (g_initialized) { tamalib_release(); g_initialized = 0; }
}
