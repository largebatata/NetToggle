package com.largebatata.nettoggle

import android.os.Binder
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.FutureTask
import kotlin.system.exitProcess

/** Public no-arg constructor required by Shizuku. A Binder, not an Android Service. */
class FirewallUserService : IFirewallService.Stub() {
    private val lock = Any()
    private val controller = FirewallController(::runCmd)

    // The CLI reads user 0, but writes matching appIds across all users.
    private fun checkOwnerUser() {
        check(Binder.getCallingUid() / 100000 == 0) { "仅支持 Android 主用户（user 0）" }
    }

    override fun getState(packageName: String): String = synchronized(lock) {
        checkOwnerUser()
        controller.getState(packageName)
    }

    override fun setBlocked(packageName: String, blocked: Boolean): String = synchronized(lock) {
        checkOwnerUser()
        controller.setBlocked(packageName, blocked)
    }

    override fun getStates(packageNames: Array<String>): Array<String> = synchronized(lock) {
        checkOwnerUser()
        controller.getStates(packageNames.toList()).toTypedArray()
    }

    private fun runCmd(args: List<String>): CommandResult {
        var process: Process? = null
        var reader: Thread? = null
        return try {
            val child = ProcessBuilder(listOf("/system/bin/cmd", "connectivity") + args)
                .redirectErrorStream(true).start()
            process = child
            child.outputStream.close()
            // Drain while waiting: error/help output can fill the pipe and deadlock waitFor.
            val outputTask = FutureTask {
                child.inputStream.use { input ->
                    val kept = ByteArrayOutputStream()
                    val buffer = ByteArray(2048)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        val keep = minOf(count, 4096 - kept.size())
                        if (keep > 0) kept.write(buffer, 0, keep)
                    }
                    kept.toString("UTF-8").trim()
                }
            }
            reader = Thread(outputTask, "nettoggle-cmd-output").apply { isDaemon = true; start() }
            if (!child.waitFor(5, TimeUnit.SECONDS)) {
                CommandResult(false, "命令超时，执行结果未知")
            } else {
                val output = outputTask.get(1, TimeUnit.SECONDS)
                CommandResult(child.exitValue() == 0, output.ifBlank {
                    if (child.exitValue() == 0) "" else "退出码 ${child.exitValue()}"
                })
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult(false, "操作已中断，执行结果未知")
        } catch (e: Exception) {
            CommandResult(false, e.message ?: "未知错误")
        } finally {
            process?.let { child ->
                if (child.isAlive) child.destroyForcibly()
                runCatching { child.inputStream.close() }
                runCatching { child.errorStream.close() }
                runCatching { child.outputStream.close() }
            }
            reader?.interrupt()
        }
    }

    override fun destroy() { exitProcess(0) }
}
