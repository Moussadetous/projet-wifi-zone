package com.wifizone.mikhmon

import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifizone.mikhmon.testing.FakeRouterOs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Parcours réel de l'utilisateur : écrans de l'application + connexion automatique dans la WebView. */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    @After fun tearDown() { MikhmonServer.stop() }

    /** Capture d'écran dans /data/local/tmp (conservée après désinstallation, récupérée par la CI). */
    private fun shot(name: String) {
        val pfd = inst.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/mikhmon-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    @Test fun setupScreen() {
        SettingsStore(ctx).reset()
        ActivityScenario.launch(SetupActivity::class.java).use {
            Thread.sleep(2000)
            shot("1-configuration")
        }
    }

    @Test fun mainScreenLogsInAutomaticallyAndShowsDashboard() {
        FakeRouterOs().start().use { fake ->
            val store = SettingsStore(ctx)
            store.reset()
            store.save(MikhmonSettings("127.0.0.1", "admin", "secret", "Mon-Routeur", "Mon Routeur",
                "wifi.zone", "FCFA", "mikhmon", "Ui-Test-Pass", routerPort = fake.port))
            MikhmonServer.stop()

            ActivityScenario.launch(MainActivity::class.java).use { sc ->
                Thread.sleep(1500)
                shot("2-demarrage")
                var html = ""
                var url = ""
                val end = System.currentTimeMillis() + 90_000
                while (System.currentTimeMillis() < end) {
                    val latch = CountDownLatch(1)
                    sc.onActivity { a ->
                        url = a.web.url.orEmpty()
                        a.web.evaluateJavascript("document.documentElement ? document.documentElement.outerHTML : ''") {
                            html = it.orEmpty(); latch.countDown()
                        }
                    }
                    latch.await(5, TimeUnit.SECONDS)
                    if (html.contains("hAP-fake")) break
                    Thread.sleep(1000)
                }
                Thread.sleep(2000) // laisse le tableau de bord finir de s'afficher
                shot("3-mikhmon")
                assertTrue("Tableau de bord non affiché. url=$url\n${html.take(1500)}\n${MikhmonServer.report()}",
                    html.contains("hAP-fake"))
                assertTrue("url=$url", url.contains("session=Mon-Routeur"))
            }
        }
    }
}
