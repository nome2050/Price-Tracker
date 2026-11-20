package com.pricetracker.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.pricetracker.app.R
import com.pricetracker.app.core.PageFetcher
import com.pricetracker.app.core.PriceParser
import com.pricetracker.app.ui.theme.PT
import org.json.JSONObject

private data class Pick(val selector: String, val text: String, val title: String, val url: String)

private fun js(mode: Boolean) = """
(function(){
 var MODE=$mode;
 if(window.__ptInstalled){window.__ptSetMode(MODE);return;}
 window.__ptInstalled=true;window.__ptMode=MODE;
 var st=document.createElement('style');
 st.textContent='.pt-hl{outline:2px dashed #3E91FF !important;background:rgba(62,145,255,.18) !important;border-radius:4px}';
 document.documentElement.appendChild(st);
 function full(e){return (e.innerText||e.textContent||'').trim();}
 function numeric(s){return s&&s.length<=60&&/\d/.test(s);}
 function esc(s){return (window.CSS&&CSS.escape)?CSS.escape(s):s.replace(/[^\w-]/g,'\\$&');}
 function seg(e){
  var t=e.tagName.toLowerCase();
  if(e.id&&document.querySelectorAll('#'+esc(e.id)).length===1)return '#'+esc(e.id);
  var cl=(e.className&&typeof e.className==='string')?e.className.trim().split(/\s+/).filter(function(c){return c&&c.length<28&&!/\d{3,}|^[a-z]{1,2}-?[A-Za-z0-9]{5,}$/.test(c)&&c!=='pt-hl';}).slice(0,2):[];
  var s=t+cl.map(function(c){return '.'+esc(c);}).join('');
  var p=e.parentElement;
  if(p){var same=Array.prototype.filter.call(p.children,function(x){return x.tagName===e.tagName;});
   if(same.length>1)s+=':nth-of-type('+(same.indexOf(e)+1)+')';}
  return s;
 }
 function css(e){
  var parts=[],cur=e;
  for(var i=0;i<7&&cur&&cur!==document.body;i++,cur=cur.parentElement){
   var s=seg(cur);parts.unshift(s);
   if(s.charAt(0)==='#')break;
   var sel=parts.join(' > ');
   try{if(document.querySelectorAll(sel).length===1&&document.querySelector(sel)===e)return sel;}catch(x){}
  }
  return parts.join(' > ');
 }
 function target(e){
  var cur=e;
  while(cur&&cur!==document.body&&!numeric(full(cur)))cur=cur.parentElement;
  if(!cur||cur===document.body)return null;
  for(var i=0;i<3;i++){var p=cur.parentElement;
   if(p&&p!==document.body&&/price|amount|cost|mrp|offer|sar|currency/i.test((p.className||'')+' '+(p.id||''))&&full(p).length<=40)cur=p;else break;}
  return cur;
 }
 function mark(){
  document.querySelectorAll('.pt-hl').forEach(function(e){e.classList.remove('pt-hl');});
  if(!window.__ptMode)return;
  var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT),n,c=0;
  while((n=w.nextNode())&&c<500){
   var t=n.nodeValue.trim();
   if(t&&t.length<=40&&/\d/.test(t)&&n.parentElement&&!/SCRIPT|STYLE|NOSCRIPT/.test(n.parentElement.tagName)){n.parentElement.classList.add('pt-hl');c++;}
  }
 }
 window.__ptSetMode=function(m){window.__ptMode=m;mark();};
 document.addEventListener('click',function(ev){
  if(!window.__ptMode)return;
  var el=target(ev.target);if(!el)return;
  ev.preventDefault();ev.stopPropagation();ev.stopImmediatePropagation();
  el.classList.remove('pt-hl');var sel=css(el);
  PT.onPick(JSON.stringify({selector:sel,text:full(el),title:document.title,url:location.href}));
 },true);
 mark();setTimeout(mark,1500);setTimeout(mark,4000);
})();
"""

private class Bridge(val cb: (String) -> Unit) {
    @JavascriptInterface fun onPick(json: String) { Handler(Looper.getMainLooper()).post { cb(json) } }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(vm: MainViewModel, onTracked: (Long) -> Unit) {
    val ctx = LocalContext.current
    var urlText by rememberSaveable { mutableStateOf("") }
    var tapMode by remember { mutableStateOf(true) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var started by rememberSaveable { mutableStateOf(false) }
    var canBack by remember { mutableStateOf(false) }
    var canFwd by remember { mutableStateOf(false) }
    val tapRef = rememberUpdatedState(tapMode)

    fun go(raw: String) {
        val t = raw.trim(); if (t.isEmpty()) return
        val u = vm.normalizeUrl(t) ?: ("https://www.google.com/search?q=" + android.net.Uri.encode(t))
        started = true; web?.loadUrl(u)
    }
    BackHandler(enabled = canBack) { web?.goBack() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { web?.goBack() }, enabled = canBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            IconButton(onClick = { web?.goForward() }, enabled = canFwd) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
            OutlinedTextField(urlText, { urlText = it }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.extraLarge,
                placeholder = { Text(stringResource(R.string.browser_url_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                keyboardActions = KeyboardActions(onGo = { go(urlText) }))
            IconButton(onClick = { web?.reload() }) { Icon(Icons.Filled.Refresh, null) }
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth())
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.browser_tap_mode), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = if (tapMode) PT.Blue else MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(tapMode, { tapMode = it; web?.evaluateJavascript(js(it), null) })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                    settings.userAgentString = PageFetcher.UA
                    addJavascriptInterface(Bridge { json ->
                        runCatching {
                            val o = JSONObject(json)
                            pick = Pick(o.getString("selector"), o.getString("text"), o.optString("title"), o.getString("url"))
                        }
                    }, "PT")
                    webChromeClient = object : WebChromeClient() { override fun onProgressChanged(v: WebView, p: Int) { progress = p } }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView, u: String?, f: Bitmap?) { u?.let { urlText = it } }
                        override fun onPageFinished(v: WebView, u: String?) {
                            canBack = v.canGoBack(); canFwd = v.canGoForward()
                            v.evaluateJavascript(js(tapRef.value), null)
                        }
                    }
                    web = this
                }
            }, onRelease = { it.destroy(); web = null })
            if (!started) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.browser_start_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.browser_start_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                listOf("amazon.sa", "noon.com/saudi-en", "jarir.com/sa-en", "extra.com/en-sa", "amazon.in").forEach { s ->
                    Surface(Modifier.padding(vertical = 4.dp).fillMaxWidth().clickable { urlText = s; go(s) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text(s, Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    pick?.let { p ->
        val parsed = PriceParser.parse(p.text)
        var name by remember(p) { mutableStateOf(p.title.take(100)) }
        var price by remember(p) { mutableStateOf(parsed?.value?.toString() ?: "") }
        ModalBottomSheet(onDismissRequest = { pick = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.browser_pick_title), style = MaterialTheme.typography.titleLarge)
                Text(p.text, style = MaterialTheme.typography.headlineMedium, color = PT.Blue)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.add_name)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                OutlinedTextField(price, { price = it }, singleLine = true, label = { Text(stringResource(R.string.add_price)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                Button(modifier = Modifier.fillMaxWidth().height(56.dp), onClick = {
                    val v = price.replace(',', '.').toDoubleOrNull()
                    if (v == null) { Toast.makeText(ctx, R.string.browser_not_price, Toast.LENGTH_SHORT).show(); return@Button }
                    vm.add(p.url, name, v, null, p.selector, parsed?.currency) { id, _ -> onTracked(id) }
                    pick = null
                }) { Text(stringResource(R.string.browser_track)) }
            }
        }
    }
}
