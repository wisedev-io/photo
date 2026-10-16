package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Harness stand-in for Android's stringResource: looks the key up in the table provided by the test. */
val LocalStringTable = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** Keys requested but missing from the table (so tests can assert there are none). */
val MissingStrings: MutableSet<String> = java.util.Collections.synchronizedSet(LinkedHashSet())

@Composable
fun stringResource(id: Int): String {
    val key = app.dvphoto.R.names[id - 1]
    return LocalStringTable.current[key] ?: run { MissingStrings.add(key); "⟦$key⟧" }
}

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String = stringResource(id).format(*formatArgs)
