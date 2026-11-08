package com.jmnext.desktop

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 运行日志（1.9.x 调试用）。
 *
 * 为什么要有它：开发容器里跑不起图形界面，界面上的问题只能靠日志判断；
 * 而"图片加载失败"这类问题，光有 URL 是查不出原因的 —— 需要 HTTP 状态码、
 * 异常类型、解码结果。所以：
 *
 *  1. 把 System.out / System.err **同时**写到终端与日志文件（tee），
 *     这样已有的日志和任何未捕获异常都会自动落盘，不必逐处改调用；
 *  2. 提供一个固定路径，测试者跑完把文件发回来即可；
 *  3. 图片加载点单独插桩（状态码/字节数/耗时/解码结果）。
 *
 * 日志里不含账号与密码 —— 登录相关只记结果与错误消息，不记凭据。
 *
 * issue #9 之后新增：
 *  - [envSummary] / [envBlock]：启动即记录 系统/版本/架构/运行时/图形后端/字体环境，
 *    崩溃时也把这段写进日志头部（"没有任何输出"这类问题正是这么查的）；
 *  - [debug]：调试级日志，默认不打印，`JMNEXT_VERBOSE=1` 或 `-Djmnext.verbose=true` 打开；
 *  - [errorOnce]：同一个 key 只打第一次，避免同一 URL 反复失败把日志刷成上千行。
 */
object Log {

    /** 日志文件放在用户主目录，容器里对应 /root/jmnext.log。 */
    private val file: File by lazy {
        File(System.getProperty("user.home") ?: ".", "jmnext.log")
    }

    private val verbose: Boolean by lazy {
        System.getenv("JMNEXT_VERBOSE") == "1" ||
            System.getProperty("jmnext.verbose")?.equals("true", ignoreCase = true) == true
    }

    /** key -> 出现次数（用于 errorOnce 聚合） */
    private val counts = ConcurrentHashMap<String, Int>()

    fun init() {
        val stream = runCatching {
            PrintStream(FileOutputStreamTee(file), true, Charsets.UTF_8)
        }.getOrNull() ?: return

        System.setOut(PrintStream(TeeStream(System.out, stream), true, Charsets.UTF_8))
        System.setErr(PrintStream(TeeStream(System.err, stream), true, Charsets.UTF_8))
        line("日志开始", "文件=${file.absolutePath}")
        // 未捕获异常：显式打一段「崩溃」区块（带环境摘要），
        // 比只靠 stderr 更容易在日志里定位；保留原 handler，不影响 AWT 自己的处理。
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                line("崩溃", "线程=${t.name} 类型=${e.javaClass.name}")
                line("崩溃", envBlock())
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                line("崩溃", sw.toString())
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** 统一格式：时间 + 标签 + 内容。图片加载与关键动作都走这里。 */
    fun line(tag: String, message: String) {
        val ts = LocalDateTime.now().toString().substring(11, 23)
        System.err.println("[$ts][$tag] $message")
    }

    /** 调试级：默认不打印（issue #9 的"精简冗余日志"）。 */
    fun debug(tag: String, message: String) {
        if (verbose) line(tag, message)
    }

    /**
     * 按同类聚合的错误日志：同一 key 首次全量打印，之后每 [everyN] 次汇总一条。
     *
     * 为什么要这样：用户日志里出现过同一批封面下载失败刷满上千行（issue #9），
     * 既淹没其它信息、又不增加信息量。聚合后仍能看出"这类错误发生了多少次"。
     */
    fun errorOnce(tag: String, key: String, message: String, everyN: Int = 50) {
        val n = (counts[key] ?: 0) + 1
        counts[key] = n
        if (n == 1 || (everyN > 0 && n % everyN == 0)) {
            line(tag, if (n == 1) message else "$message（同类已出现 $n 次）")
        }
    }

    /** 把异常打到日志里（含类型与消息，便于区分超时/404/解码失败）。 */
    fun error(tag: String, message: String, t: Throwable? = null) {
        line(tag, if (t == null) message else "$message | ${t.javaClass.simpleName}: ${t.message}")
    }

    /** 单行环境摘要（issue #9）：拿到用户日志时先看这一行，判断是不是平台差异。 */
    fun envSummary(): String {
        val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
        val arch = System.getProperty("os.arch")
        val java = "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})"
        val backend = if (System.getProperty("skiko.renderApi") == "SOFTWARE") "软件渲染" else "GPU"
        return "应用 $DESKTOP_VERSION | 系统 $os | 架构 $arch | 运行时 Java $java | 图形后端 $backend | 字体 ${fontInfo()}"
    }

    /** 多行环境块：崩溃时写进日志头部。 */
    fun envBlock(): String = "---- 启动环境 ----\n${envSummary()}\n------------------"

    /**
     * 字体环境（与 issue #7「有界面无文字」直接相关）：
     * 默认字体族 + 可用字体族数量。字体被精简或解析失败时，这两项会立刻露出来。
     */
    /**
     * 字体环境（与 issue #7「有界面无文字」直接相关）。
     *
     * 软件渲染下文本出不来，最常见的原因就是**没有可用的中文字体**（系统被精简、
     * 字体目录被清理、或字体缓存损坏）。所以这里不只报"字体族数量"，还明确报出
     * 是否找到了常见中文字体，并给出前几个字体族样例便于比对。
     */
    private fun fontInfo(): String = runCatching {
        val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
        val def = Font("SansSerif", Font.PLAIN, 12).family
        val cjkCandidates = listOf(
            "Microsoft YaHei", "微软雅黑", "SimSun", "宋体", "SimHei", "黑体",
            "Noto Sans CJK SC", "Source Han Sans SC", "WenQuanYi Micro Hei"
        )
        val cjk = cjkCandidates.firstOrNull { c -> families.any { it.equals(c, ignoreCase = true) } }
        val sample = families.take(6).joinToString("、")
        "$def（字体族 ${families.size}；中文字体 ${cjk ?: "未找到常见中文字体"}；样例 $sample）"
    }.getOrElse { "取不到：${it.javaClass.simpleName}: ${it.message}" }

    private class FileOutputStreamTee(f: File) : OutputStream() {
        private val out = java.io.FileOutputStream(f, true)
        override fun write(b: Int) { out.write(b); out.flush() }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); out.flush() }
    }

    private class TeeStream(private val a: OutputStream, private val b: OutputStream) : OutputStream() {
        override fun write(x: Int) { runCatching { a.write(x); a.flush() }; runCatching { b.write(x); b.flush() } }
        override fun write(x: ByteArray, off: Int, len: Int) {
            runCatching { a.write(x, off, len); a.flush() }
            runCatching { b.write(x, off, len); b.flush() }
        }
    }
}
