package co.edu.unal.reto10_jm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import co.edu.unal.reto10_jm.ui.theme.Reto10_JMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dashboard = ViewModelProvider(this)[DashboardViewModel::class.java]
        val events = ViewModelProvider(this)[EventViewModel::class.java]
        val consolidated = ViewModelProvider(this)[ConsolidatedViewModel::class.java]
        setContent {
            Reto10_JMTheme {
                var module by rememberSaveable { mutableIntStateOf(0) }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var expanded by rememberSaveable { mutableStateOf(false) }

                Scaffold(
                    bottomBar = {
                        if (!expanded && events.document == null) {
                            NavigationBar {
                                listOf("Dashboard", "Evento", "Consolidado").forEachIndexed { index, name ->
                                    NavigationBarItem(
                                        selected = index == module,
                                        onClick = { module = index },
                                        icon = { Text(name) }
                                    )
                                }
                            }
                        }
                    }
                ) { insets ->
                    val content = Modifier.padding(insets).fillMaxSize()
                    when (module) {
                        0 -> DashboardScreen(dashboard, tab, { tab = it }, expanded, { expanded = it }, content)
                        1 -> EventScreen(events, content)
                        else -> ConsolidatedScreen(consolidated, content)
                    }
                }
            }
        }
    }
}
