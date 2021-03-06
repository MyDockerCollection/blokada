package buildtype

import android.app.Application
import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import com.github.salomonbrys.kodein.*
import gs.environment.Environment
import gs.environment.Journal
import gs.environment.Time
import gs.environment.Worker
import gs.property.BasicPersistence
import gs.property.Device
import gs.property.IProperty
import gs.property.newPersistedProperty
import org.blokada.R
import java.io.PrintWriter
import java.io.StringWriter

fun newBuildTypeModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<Journal>(overrides = true) with singleton {
            OfficialJournal(ctx = ctx)
        }
        bind<Events>() with singleton {
            EventsImpl(kctx = with("gscore").instance(), xx = lazy)
        }
        onReady {
            val e: Events = instance()
            val d: Device = instance()

            // I assume this will happen at least once a day
            d.screenOn.doWhenChanged().then { e.lastDailyMillis.refresh() }

            // This will happen when loading the app to memory
            e.lastDailyMillis.refresh()
        }
    }
}

abstract class Events {
    abstract val lastDailyMillis: IProperty<Long>
}

class EventsImpl(
        private val kctx: Worker,
        private val xx: Environment,
        private val time: Time = xx().instance(),
        private val j: Journal = xx().instance()
) : Events() {
    override val lastDailyMillis = newPersistedProperty(kctx, BasicPersistence(xx, "daily"), { 0L },
            refresh = {
                j.event("daily");
                time.now()
            },
            shouldRefresh = { !DateUtils.isToday(it) })
}

class OfficialJournal(
        private val ctx: Context
) : Journal {

    private val amp by lazy {
        val a = JournalFactory.instance.initialize(ctx, ctx.getString(R.string.journal_key))

        try {
            val app = ctx as Application
            a.enableForegroundTracking(app)
        } catch (e: Exception) {
            Log.e("blokada", "journal: failed to get application", e)
        }
        a
    }

    private var userId: String? = null
    private val userProperties = mutableMapOf<String, String>()

    override fun setUserId(id: String) {
        userId = id
    }

    override fun setUserProperty(key: String, value: Any) {
        userProperties.put(key, value.toString())
    }

    override fun event(vararg events: Any) {
        events.forEach { event ->
            amp.logEvent(event.toString())
            Log.i("blokada", "event: $event")
        }
    }

    override fun log(vararg errors: Any) {
        errors.forEach { error ->
            Log.v("blokada", error.toString())
            if (error is Exception) {
                Log.e("blokada", error.message)
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                Log.e("blokada", sw.toString())
            }
        }
    }

}

object JournalFactory {

    internal val instances: MutableMap<String, JournalClient> = HashMap()

    val instance: JournalClient
        get() = getInstance()

    @Synchronized
    fun getInstance(instance: String = ""): JournalClient {
        var instance = instance
        instance = Utils.normalizeInstanceName(instance!!)
        var client: JournalClient? = instances[instance]
        if (client == null) {
            client = JournalClient(instance)
            instances[instance] = client
        }
        return client
    }
}


