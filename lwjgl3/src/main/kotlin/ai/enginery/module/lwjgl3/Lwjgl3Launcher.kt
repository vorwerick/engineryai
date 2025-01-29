@file:JvmName("Lwjgl3Launcher")

package ai.enginery.module.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import ai.enginery.module.Main
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import java.io.PrintStream

/** Launches the desktop (LWJGL3) application. */
fun main() {
    // This handles macOS support and helps on Windows.
    if (StartupHelper.startNewJvmIfRequired())
        return
    Lwjgl3Application(Main(), Lwjgl3ApplicationConfiguration().apply {
        //setWindowedMode(1200, 900)
        //setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL31, 3, 1)
        setDecorated(false)
        setForegroundFPS(30)
        setIdleFPS(30)
        enableGLDebugOutput(true, PrintStream(System.out))
        //
        //setWindowIcon(*(arrayOf(128, 64, 32, 16).map { "libgdx$it.png" }.toTypedArray()))
    })
}
