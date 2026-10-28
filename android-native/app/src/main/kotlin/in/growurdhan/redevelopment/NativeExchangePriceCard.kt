package `in`.growurdhan.redevelopment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun NativeExchangePriceCard(busy: Boolean,
 onRefresh: (NativeExchangeEodCatalog.Exchange, String) -> Unit) {
 var exchange by remember { mutableStateOf(NativeExchangeEodCatalog.Exchange.NSE) }
 var url by remember { mutableStateOf("") }
 Column {
  Text("Public official dated close, not a live quote. Manual refresh only.")
  Text("Only a whole-market official NSE/BSE BhavCopy URL is sent, never holdings or PAN.")
  Row {
   for (option in NativeExchangeEodCatalog.Exchange.entries) {
    OutlinedButton(onClick = { exchange = option },
     modifier = Modifier.heightIn(min = 48.dp).testTag("m30-source-" + option.name)) {
     Text(option.name + if (option == exchange) " selected" else "")
    }
   }
  }
  OutlinedTextField(url, { url = it },
   label = { Text("Official BhavCopy ZIP HTTPS URL") },
   modifier = Modifier.fillMaxWidth().testTag("m30-source-url"))
  OutlinedButton(onClick = { onRefresh(exchange, url.trim()) },
   enabled = !busy && url.isNotBlank(),
   modifier = Modifier.heightIn(min = 48.dp).testTag("m30-import-close")) {
   Text("Refresh dated close")
  }
  Text("No automatic requests. Failed downloads retain encrypted dated prices.")
 }
}
