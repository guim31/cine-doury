package fr.guim.cinedoury.feature.update

import android.content.Context
import fr.guim.cinedoury.core.update.ReleaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

/** `:feature:update` for Koin: one repository for the whole app, outliving the screens. */
val updateModule =
    module {
        single<PackageGate> { AndroidPackageGate(androidContext()) }
        single { ReleaseClient() }
        single {
            val context = androidContext()
            UpdateRepository(
                client = get(),
                preferences = get(),
                gate = get(),
                versionName = context.versionName(),
                downloads = File(context.cacheDir, "updates"),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            )
        }
    }

/** The version of the app, as Gradle wrote it from the tag (« 1.2.0 ») or not (« 0.1.0-dev »). */
fun Context.versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
