package com.jmnext.desktop

import com.jmnext.data.auth.SecretKeyProvider
import java.io.File
import java.nio.file.Files
import java.util.prefs.Preferences

/**
 * 凭据存储相关改动的**可执行自检**（issue #11 / #12）。
 *
 * 为什么要有它：这些改动此前只做了编译验证，而"编译通过"并不等于逻辑对。
 * 这里把关键路径真的跑一遍（口令包裹/解开/错口令/销毁、节点迁移与清除、
 * 无图形环境下首次询问的安全失败路径），并在失败时以非零退出码结束。
 *
 * 运行方式（在 desktop 目录）：
 * ```
 * gradle --offline compileKotlin
 * java -cp build/classes/kotlin/main:../shared/build/classes/kotlin/main:<kotlin-stdlib.jar> \
 *      com.jmnext.desktop.SelfCheckKt
 * ```
 */
object SelfCheck {
    private var pass = 0
    private var fail = 0

    private fun check(what: String, ok: Boolean) {
        println((if (ok) "  通过: " else "  失败: ") + what)
        if (ok) pass++ else fail++
    }

    /** 跑完全部检查并返回失败项数（0 表示全通过）。供 Gradle 测试调用，不退出进程。 */
    @JvmStatic
    fun run(): Int {
        pass = 0
        fail = 0
        println("== 凭据存储自检 ==")
        checkPassphrase()
        checkPreferences()
        checkFreshInstallDecision()
        checkI18n()
        println("== 结果：通过 " + pass + " 项，失败 " + fail + " 项 ==")
        return fail
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (run() > 0) System.exit(1)
    }

    /** 多语言表自检：表非空、简中键齐、各语言没有空值。 */
    private fun checkI18n() {
        println("== 多语言自检 ==")
        val zh = com.jmnext.desktop.i18n.stringsFor("zh-CN")
        check("多语言：简中表非空", zh.isNotEmpty())
        check("多语言：简中表没有空值", zh.values.none { it.isBlank() })
        for (loc in listOf("zh-rTW", "zh-rHK", "en", "ja", "ru", "ko")) {
            val t = com.jmnext.desktop.i18n.stringsFor(loc)
            check("多语言： 有内容", t.isNotEmpty())
            check("多语言： 没有空值", t.values.none { it.isBlank() })
            check("多语言： 键集合与简中一致", t.keys == zh.keys)
        }
        val byZh = com.jmnext.desktop.i18n.byZhFor("en")
        check("多语言：英文反查表非空", byZh.isNotEmpty())
    }

    /** 口令保护：包裹 -> 解开 -> 错口令 fail closed -> forget 删除文件。 */
    private fun checkPassphrase() {
        val dir = Files.createTempDirectory("selfcheck-keys").toFile()
        val good = PassphraseKeyProvider(dir) { "correct-horse".toCharArray() }
        val k1 = good.aesKey(ALIAS)
        check("口令保护：首次调用生成并包裹密钥", k1 != null && File(dir, "$ALIAS.wrapped").length() > 0)

        // 新实例（清掉进程内缓存）用同一口令解开
        val again = PassphraseKeyProvider(dir) { "correct-horse".toCharArray() }
        val k2 = again.aesKey(ALIAS)
        check("口令保护：同一口令可解出同一把密钥",
            k1 != null && k2 != null && k1.encoded.contentEquals(k2.encoded))

        val wrong = PassphraseKeyProvider(dir) { "wrong-pass".toCharArray() }
        check("口令保护：错口令 fail closed（返回 null 而不是明文兜底）", wrong.aesKey(ALIAS) == null)
        // 包裹文件被截断/损坏时必须 fail closed（返回 null 且不抛异常）
        val wrapped = File(dir, "$ALIAS.wrapped")
        val original = wrapped.readBytes()
        wrapped.writeBytes(original.copyOfRange(0, original.size / 2))
        check("口令保护：包裹文件损坏时 fail closed（返回 null，不抛）",
            PassphraseKeyProvider(dir) { "correct-horse".toCharArray() }.aesKey(ALIAS) == null)
        wrapped.writeBytes(original)

        good.forget(ALIAS)
        check("口令保护：forget 删除包裹文件（登出即销毁密钥材料）", !File(dir, "$ALIAS.wrapped").exists())
        dir.deleteRecursively()
    }

    /** Preferences：新节点读写、旧节点迁移、clear 两个节点都清（issue #11）。 */
    private fun checkPreferences() {
        val node = "selfcheck_" + System.nanoTime()
        val legacy = Preferences.userRoot().node("com/jmcomic_next/$node")
        legacy.put("jwt", "OLD-CIPHERTEXT")
        legacy.put("lastPage", "42")
        legacy.putBoolean("dark", true)
        legacy.putLong("ts", 123456789L)
        legacy.flush()

        val store = PreferencesKeyValueStore(node)
        check("节点迁移：读到旧节点的值", store.getString("jwt", null) == "OLD-CIPHERTEXT")
        check("节点迁移：整型键按类型读回", store.getInt("lastPage", 0) == 42)
        check("节点迁移：布尔键按类型读回", store.getBoolean("dark", false))
        check("节点迁移：长整型键按类型读回", store.getLong("ts", 0L) == 123456789L)
        val newPrefs = Preferences.userRoot().node("com/jmnext/$node")
        check("节点迁移：旧值已写入新节点", newPrefs.get("jwt", null) == "OLD-CIPHERTEXT")
        check("节点迁移：旧节点仍保留（可回退）", legacy.get("jwt", null) == "OLD-CIPHERTEXT")

        // issue #15 回归：旧节点不存在时，构造 store 并读取**不得**把它创建出来。
        // 依据：java.util.prefs 的 node() 会创建节点，nodeExists() 不会 —— 已用一次性
        // `-Djava.util.prefs.userRoot=<tmp>` 的 Java 探针实测（只 nodeExists 时业务节点数 0；
        // 调 node() 后立刻出现 com/... 节点）。
        val freshNode = "selfcheck_fresh_" + System.nanoTime()
        val freshLegacyPath = "com/jmcomic_next/$freshNode"
        val legacyExistedBefore = Preferences.userRoot().nodeExists(freshLegacyPath)
        val freshStore = PreferencesKeyValueStore(freshNode)
        freshStore.getString("jwt", null)                       // 触发一次读（含迁移尝试）
        freshStore.putString("jwt", "NEW")                      // 触发一次写
        val legacyExistsAfter = Preferences.userRoot().nodeExists(freshLegacyPath)
        check(
            "issue #15：旧节点不存在时，读写新节点不会创建旧节点",
            !legacyExistedBefore && !legacyExistsAfter
        )
        runCatching { Preferences.userRoot().node("com/jmnext/$freshNode").removeNode() }

        store.clear()
        check("clear：新节点已清空", newPrefs.get("jwt", null) == null)
        check("clear：旧节点也已清空（否则登出后旧密文会被复活）", legacy.get("jwt", null) == null)
        runCatching { newPrefs.removeNode(); legacy.removeNode() }
    }

    /**
     * 全新安装 + 无图形环境：询问弹不出来时**安全落回原实现**，并且**不能**写下"不再询问"标记。
     *
     * issue #16 的教训：这条自检原先断言"写下了不再询问的标记"，等于把错误行为写成期望行为 ——
     * 在无图形环境里根本没人被问过，却记成"用户拒绝"，新装用户于是永远不再被询问，
     * 凭据一直按老方式明文保存。现在断言的是相反的事实。
     */
    private fun checkFreshInstallDecision() {
        val dir = Files.createTempDirectory("selfcheck-fresh").toFile()
        val fallback = object : SecretKeyProvider {
            override fun aesKey(alias: String) = null
        }
        val chosen = runCatching { PassphraseKeyProvider.maybeEnable(dir, { fallback }, ALIAS) }.getOrNull()
        check("全新安装：maybeEnable 不抛异常", chosen != null)
        check("全新安装：安全落回原实现", chosen === fallback)
        check(
            "全新安装：对话框不可用时**不写**拒绝标记（issue #16）",
            !File(dir, ".passphrase-declined").exists()
        )
        // 第二次调用仍然走询问分支（仍不写标记），也就是"下次启动会再问"
        val again = runCatching { PassphraseKeyProvider.maybeEnable(dir, { fallback }, ALIAS) }.getOrNull()
        check("全新安装：再次调用仍不写拒绝标记，下次启动会继续询问", again === fallback && !File(dir, ".passphrase-declined").exists())

        // "未开启口令保护"的一次性提示：无图形环境时**不能**写"已提示"标记。
        // 否则等于"没人看过却记成已提示"，用户永远不会再被告知（与 issue #16 同一类错误）。
        // 这里构造"老安装"场景（存在明文密钥文件）以走到该分支。
        File(dir, "$ALIAS.key").writeBytes(ByteArray(32) { 1 })
        val legacy = runCatching { PassphraseKeyProvider.maybeEnable(dir, { fallback }, ALIAS) }.getOrNull()
        check("未开启口令保护：老安装场景仍安全落回原实现", legacy === fallback)
        check(
            "未开启口令保护：无图形环境不写「已提示」标记（下次启动仍会提示）",
            !File(dir, ".passphrase-notice-shown").exists()
        )
        dir.deleteRecursively()
    }

    private const val ALIAS = "selfcheck_alias"
}

fun main() = SelfCheck.main(emptyArray())
