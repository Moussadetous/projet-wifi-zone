package com.wifizone.mikhmon

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifizone.mikhmon.testing.FakeRouterOs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Parcours réel de l'application sur Android, avec un faux routeur et un compte simulé (sans réseau). */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private var fake: FakeRouterOs? = null

    @Before fun init() { Services.init(ctx.applicationContext as android.app.Application) }

    @After fun tearDown() {
        MikhmonServer.stop()
        fake?.close()
        Services.account.seedForTests(null)
    }

    private fun shot(name: String) {
        val pfd = inst.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/mikhmon-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private fun account(status: String, items: List<String> = emptyList(), days: Long = 5): String {
        val end = OffsetDateTime.now(ZoneOffset.UTC).plusDays(if (status == "trial") days else -1).withNano(0).toString()
        return """{"user":{"id":"u1","name":"Awa Traoré","email":"awa@gmail.com","balance":1000},
            "app":{"id":"mikhmon-droid","name":"Mikhmon Android","trial_days":7,"lifetime_price":4000,"item_price":1000,"item_prices":{}},
            "license":{"status":"$status","trial_end":"$end","days_left":$days},
            "items":[${items.joinToString(",") { "\"$it\"" }}],"pending":null,"currency":"francs CFA"}"""
    }

    private fun routerReady() {
        val f = FakeRouterOs().start().also { fake = it }
        val store = SettingsStore(ctx)
        store.reset()
        store.save(MikhmonSettings("127.0.0.1", "admin", "secret", "Mon-Routeur", "Wifi Zone Ouaga", "wifi.zone", "F", "mikhmon", "Ui-Test-Pass", routerPort = f.port))
        Services.router.reset()
    }

    private fun open(screen: String?, wait: Long, name: String) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (screen != null) i.putExtra("open", screen)
        ActivityScenario.launch<MainActivity>(i).use {
            Thread.sleep(wait)
            shot(name)
        }
    }

    @Test fun setupScreen() {
        SettingsStore(ctx).reset()
        Services.account.seedForTests(null)
        open(null, 2500, "01-configuration")
    }

    @Test fun loginAndPaywall() {
        routerReady()
        Services.account.seedForTests(null)
        open(null, 3000, "02-connexion-compte")
        Services.account.seedForTests(account("expired"))
        open(null, 3000, "03-essai-termine")
    }

    @Test fun mainScreens() {
        routerReady()
        Services.design.templateId = "tpl-carte-saphir"
        Services.account.seedForTests(account("trial", listOf("tpl-carte-saphir", "tpl-neon-cyan")))
        open(null, 6000, "04-accueil")
        open("clients", 7000, "05-clients")
        open("trafic", 9000, "06-trafic")
        open("tickets", 5000, "07-tickets")
        open("journal", 4000, "08-journal")
        open("studio", 7000, "09-studio")
        open("compte", 3000, "10-compte")
    }
}
