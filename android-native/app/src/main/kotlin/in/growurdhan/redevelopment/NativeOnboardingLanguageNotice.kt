package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Optional bilingual help; never substitutes for versioned English Terms acceptance. */
@Composable
internal fun NativeOnboardingLanguageNotice() {
    var showHindi by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { showHindi = !showHindi },
            modifier = Modifier.heightIn(min = 48.dp)
                .testTag("m31-hindi-toggle")) {
            Text(if (showHindi) "English guidance" else "हिंदी मार्गदर्शन")
        }
        if (showHindi) {
            Text("आपकी वित्तीय जानकारी केवल इस डिवाइस पर संग्रहीत रहती है।",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("m31-hindi-local-only"))
            Text("इस संस्करण में वित्तीय डेटा का बैकअप या पुनर्स्थापन उपलब्ध नहीं है। " +
                "डिवाइस खोने या ऐप हटाने पर आपका वित्तीय डेटा स्थायी रूप से मिट सकता है।",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("m31-hindi-loss-warning"))
            Text("यह सहायता पाठ है; नियमों और गोपनीयता सहमति को अलग से स्वीकार करना आवश्यक है।",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("m31-hindi-terms-distinct"))
        }
    }
}
