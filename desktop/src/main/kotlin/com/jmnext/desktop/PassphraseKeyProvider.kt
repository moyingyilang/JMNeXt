package com.jmnext.desktop

import com.jmnext.desktop.i18n.I18n
import com.jmnext.data.auth.SecretKeyProvider
import java.awt.GraphicsEnvironment
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.swing.JOptionPane
import javax.swing.JPasswordField
import javax.swing.SwingUtilities

/**
 * [SecretKeyProvider] 的**口令保护**实现（issue #12 第二步）。
 *
 * 为什么需要它：桌面端把 AES 密钥以明文文件存放时，**同一个用户下的任意进程都能读走**，
 * 加密等于无效（PoC 正是这么解的）。ACL 加固只能挡住"同机其它用户/离线拷贝"，
 * 挡不住同用户进程；DPAPI 也挡不住（同用户一样能解密）。真正能挡住的是
 * **攻击者没有的那份材料** —— 也就是口令。
 *
 * 存储格式（`keys/<alias>.wrapped`，原始字节）：
 * `salt(16) || iv(12) || AES-GCM 密文(32 字节密钥 + 16 字节 tag)`
 * 其中包裹密钥由口令经 PBKDF2-HMAC-SHA256（12 万次迭代）派生。
 *
 * 行为约定：
 * - 文件不存在 -> 生成新密钥并用口令包裹后落盘；
 * - 文件存在 -> 用口令解开；口令错误 -> 返回 null（**fail closed**，调用方拒绝落盘/读取，
 *   不会退化成明文存储）；
 * - 口令来源：环境变量 `JMNEXT_KEY_PASSPHRASE`（便于无界面/自动化测试），
 *   否则弹出一次图形口令框（Swing，仅需输入一次，进程内缓存）。
 *
 * **默认不启用**：只有当 `keys/<alias>.wrapped` 已存在，或显式设置 `JMNEXT_KEY_PASSPHRASE_ENABLE=1`
 * 时才切换到本实现（见 [maybeEnable]），因此老用户行为完全不变。
 */
class PassphraseKeyProvider(
    private val dir: File,
    private val prompt: (String) -> CharArray? = ::askPassphraseDialog
) : SecretKeyProvider {

    private val random = SecureRandom()
    private var cached: SecretKey? = null

    override fun aesKey(alias: String): SecretKey? = runCatching {
        cached?.let { return@runCatching it }
        val wrapped = File(dir, "$alias.wrapped")
        val pass = passphrase()
        if (pass == null || pass.isEmpty()) {
            println("[凭据] 未提供口令，无法使用口令保护的密钥（本次会话不持久化令牌）")
            return@runCatching null
        }
        val key = if (wrapped.exists() && wrapped.length() > 0) unwrap(wrapped, pass) else wrapNew(wrapped, pass)
        cached = key
        key
    }.getOrElse {
        // 口令错误会走到这里（AES-GCM 校验失败），必须明确告知而不是静默失败
        println("[凭据] 解不开被口令保护的密钥（口令错误或文件损坏）：${it.javaClass.simpleName}")
        null
    }

    override fun forget(alias: String) {
        cached = null
        runCatching { File(dir, "$alias.wrapped").delete() }
    }

    private fun passphrase(): CharArray? =
        System.getenv("JMNEXT_KEY_PASSPHRASE")?.toCharArray()
            ?: prompt(I18n.tr("请输入密钥口令（用于保护本机登录凭据）"))

    private fun derive(pass: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pass, salt, ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun unwrap(file: File, pass: CharArray): SecretKey {
        val bytes = file.readBytes()
        require(bytes.size > SALT_LEN + IV_LEN) { "包裹文件长度不合法" }
        val salt = bytes.copyOfRange(0, SALT_LEN)
        val iv = bytes.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val body = bytes.copyOfRange(SALT_LEN + IV_LEN, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(pass, salt), GCMParameterSpec(TAG_BITS, iv))
        return SecretKeySpec(cipher.doFinal(body), "AES")
    }

    private fun wrapNew(file: File, pass: CharArray): SecretKey {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(pass, salt), GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(key.encoded)
        dir.mkdirs()
        file.writeBytes(salt + iv + body)
        harden(file)
        println("[凭据] 已用口令保护密钥：${file.name}（PBKDF2 迭代 $ITERATIONS 次）")
        return key
    }

    /** 与 FileKeyProvider 同样的权限收紧（POSIX 600 + Windows 移除宽泛主体）。 */
    private fun harden(file: File) {
        file.setReadable(false, false); file.setReadable(true, true)
        file.setWritable(false, false); file.setWritable(true, true)
    }

    companion object {
        private const val SALT_LEN = 16
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
        private const val ITERATIONS = 120_000
        private const val DECLINED_MARKER = ".passphrase-declined"

        /**
         * 装配策略（issue #12）：
         *
         * 1. 已存在 `keys/<alias>.wrapped` -> 直接用口令保护（说明此前已开启）；
         * 2. `JMNEXT_KEY_PASSPHRASE_ENABLE=0` -> 强制关闭（企业/自动化可用来关掉询问）；
         * 3. `JMNEXT_KEY_PASSPHRASE_ENABLE=1` 或已提供 `JMNEXT_KEY_PASSPHRASE` -> 直接开启；
         * 4. 用户此前选过"暂不使用"（有标记文件）-> 沿用原实现，不再打扰；
         * 5. 目录里已有明文密钥文件（老安装）-> **不动**，行为与以前完全一致；
         * 6. 以上都不满足（全新安装）-> 弹一次对话框说明风险，用户可选"设置口令"或"暂不使用"，
         *    选后者会写标记文件，之后不再询问。
         *
         * 为什么对全新安装默认开启：桌面端把密钥明文放文件里时，同机其它程序能读走并解密
         * （已有公开的读取示例）。老安装保持原状是为了不破坏既有用户的登录状态。
         */
        fun maybeEnable(dir: File, fallback: () -> SecretKeyProvider, alias: String = "jm_session_v1"): SecretKeyProvider {
            if (File(dir, "$alias.wrapped").exists()) return PassphraseKeyProvider(dir)
            val enableFlag = System.getenv("JMNEXT_KEY_PASSPHRASE_ENABLE")
            if (enableFlag == "0") return fallback()
            if (enableFlag == "1" || System.getenv("JMNEXT_KEY_PASSPHRASE") != null) {
                return PassphraseKeyProvider(dir)
            }
            val declined = File(dir, DECLINED_MARKER)
            if (declined.exists()) {
                noticeUnprotectedOnce(dir, I18n.tr("你此前选择暂不使用口令保护"))
                return fallback()
            }
            if (File(dir, "$alias.key").exists()) {
                noticeUnprotectedOnce(dir, "检测到老安装：密钥以明文文件保存")
                return fallback()                                      // 老安装：不动
            }
            return when (askFirstRunChoice()) {
                FirstRunChoice.ENABLE -> PassphraseKeyProvider(dir)
                FirstRunChoice.DECLINE -> {
                    runCatching { dir.mkdirs(); declined.writeText("declined") }
                    println("[凭据] 用户选择暂不使用口令保护（以后可设 JMNEXT_KEY_PASSPHRASE_ENABLE=1 开启）")
                    noticeUnprotectedOnce(dir, I18n.tr("你选择了暂不使用口令保护"))
                    fallback()
                }
                // issue #16：对话框没能弹出来（无图形环境/EDT 限制等）时**不能**写"拒绝"标记，
                // 否则等于"没人问过就记为用户拒绝"，新装用户永远不再被询问，凭据会一直按老方式明文存。
                // 这种情况保持原行为，但不落标记，下次启动继续尝试询问。
                FirstRunChoice.UNAVAILABLE -> {
                    println("[凭据] 无法弹出询问对话框（无图形环境或界面线程限制），本次沿用原实现；下次启动会再问")
                    fallback()
                }
            }
        }

        /** 首次询问的三种结果。区分"用户拒绝"与"根本没问成"是 issue #16 的关键。 */
        private enum class FirstRunChoice { ENABLE, DECLINE, UNAVAILABLE }

        /** 一次性安全提示的标记文件（与"拒绝询问"的 [DECLINED_MARKER] 分开，互不影响）。 */
        private const val NOTICE_MARKER = ".passphrase-notice-shown"

        /**
         * 当应用在**未开启口令保护**的状态下运行时，提示一次（不是每次启动都打扰）。
         *
         * 沿用 issue #16 的教训：**对话框没能弹出来时不写"已提示"标记**，
         * 否则等于"没人看过却记成已提示"，用户永远不会再被告知。这种情况只写日志，下次启动再试。
         */
        private fun noticeUnprotectedOnce(dir: File, reason: String) {
            val marker = File(dir, NOTICE_MARKER)
            if (marker.exists()) return
            val howTo = "要开启口令保护：删除配置目录下的 " + DECLINED_MARKER +
                " 后重启，或设置环境变量 JMNEXT_KEY_PASSPHRASE_ENABLE=1。"
            if (GraphicsEnvironment.isHeadless()) {
                println("[凭据] 未开启口令保护（$reason）：本机其它程序可读取并解密登录凭据。$howTo")
                println("[凭据] （当前环境无图形界面，本次不写提示标记，下次启动会再提示）")
                return
            }
            var shown = false
            runCatching {
                runOnEdtAndWait {
                    JOptionPane.showMessageDialog(
                        null,
                        "本机登录凭据未开启口令保护。\n\n" +
                            "原因：$reason\n\n" +
                            "现状：密钥以明文文件保存在配置目录，同一台机器上的其它程序\n" +
                            "可以读取并解密你的登录凭据（已有人公开了这样的读取程序）。\n\n" +
                            howTo + "\n" +
                            "（本提示只出现一次。）",
                        I18n.tr("登录凭据保护提示"),
                        JOptionPane.WARNING_MESSAGE
                    )
                    shown = true
                }
            }
            if (shown) {
                runCatching { dir.mkdirs(); marker.writeText("shown") }
                println("[凭据] 已一次性提示：当前未开启口令保护（$reason）")
            } else {
                println("[凭据] 未能弹出未开启口令保护的提示（$reason），本次不写标记，下次启动会再试")
            }
        }

        /** 全新安装时问一次；可拒绝（拒绝后写标记文件，不再询问）。 */
        private fun askFirstRunChoice(): FirstRunChoice {
            if (GraphicsEnvironment.isHeadless()) return FirstRunChoice.UNAVAILABLE
            var choice = FirstRunChoice.UNAVAILABLE
            val ok = runCatching {
                runOnEdtAndWait {
                    val msg = "是否用口令保护本机登录凭据？\n\n" +
                        "背景：桌面端要在本机保存登录令牌。若只存在文件里，同一台机器上的其它程序\n" +
                        "也能读走并解密（已有公开的读取示例）。设置口令后，没有口令就解不开。\n\n" +
                        "「设置口令」：每次启动需输入一次口令；\n" +
                        "「暂不使用」：与以前相同（本机其它程序可读），以后可再开启。"
                    val opts = arrayOf(I18n.tr("设置口令"), I18n.tr("暂不使用"))
                    val r = JOptionPane.showOptionDialog(
                        null, msg, I18n.tr("登录凭据保护"), JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]
                    )
                    // 关掉对话框（返回 CLOSED_OPTION）视为"没做选择"，不算拒绝，下次再问
                    choice = when (r) {
                        0 -> FirstRunChoice.ENABLE
                        1 -> FirstRunChoice.DECLINE
                        else -> FirstRunChoice.UNAVAILABLE
                    }
                }
            }
            return if (ok.isSuccess) choice else FirstRunChoice.UNAVAILABLE
        }

        /**
         * 在 EDT 上执行；**已经在 EDT 上时直接执行**。
         *
         * issue #16 的次要原因：`SwingUtilities.invokeAndWait` 在事件分发线程上调用会抛
         * "Cannot call invokeAndWait from the event dispatcher thread"，异常被吞掉后
         * 表现就是"弹窗没出现却记为已处理"。Compose Desktop 启动阶段正好可能在 EDT 上。
         */
        private fun runOnEdtAndWait(block: () -> Unit) {
            if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
        }

        private fun askPassphraseDialog(title: String): CharArray? {
            var out: CharArray? = null
            runCatching {
                SwingUtilities.invokeAndWait {
                    val field = JPasswordField(24)
                    val ok = JOptionPane.showConfirmDialog(
                        null, field, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
                    )
                    out = if (ok == JOptionPane.OK_OPTION) field.password else null
                }
            }
            return out
        }
    }
}
