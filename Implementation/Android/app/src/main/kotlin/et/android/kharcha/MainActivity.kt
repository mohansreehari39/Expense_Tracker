package et.android.kharcha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import et.android.kharcha.ui.KharchaApp
import et.android.kharcha.ui.theme.KharchaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KharchaTheme {
                KharchaApp()
            }
        }
    }
}
