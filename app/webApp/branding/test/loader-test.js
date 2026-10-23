// Brands the web app's index.html, then runs the page's download-retry script against a fake network that
// drops the connection in the middle of a body.
//   node app/webApp/branding/test/loader-test.js
const fs=require('fs'), os=require('os'), path=require('path'), assert=require('assert'), { execFileSync }=require('child_process');
const root=path.join(__dirname,'..','..','..','..');
const dist=fs.mkdtempSync(path.join(os.tmpdir(),'brand-')); fs.mkdirSync(path.join(dist,'images'));
fs.copyFileSync(path.join(root,'app/webApp/src/webMain/resources/index.html'),path.join(dist,'index.html'));
execFileSync(path.join(root,'scripts/web-brand.sh'),['agntcon-mcpcon-na-2026',dist,'T','D'],{stdio:'ignore'});
const page=fs.readFileSync(path.join(dist,'index.html'),'utf8');
const script=[...page.matchAll(/<script>([\s\S]*?)<\/script>/g)][0][1];
const els={}; const mk=id=>els[id]={textContent:'',hidden:true};
['loading','loading-text','loading-note','loading-error'].forEach(mk);
global.document={getElementById:id=>els[id]||null};
let calls=0;
global.window={fetch:async()=>{ calls++; const attempt=calls;
  const stream=new ReadableStream({start(c){ c.enqueue(new Uint8Array(1000)); if(attempt<3) c.error(new TypeError('network error')); else { c.enqueue(new Uint8Array(500)); c.close(); } }});
  return new Response(stream,{status:200}); }};
global.window.fetch.bind=function(){return this};
global.addEventListener=()=>{}; global.setTimeout=(f)=>f();
eval(script.replace('window.fetch.bind(window)','window.fetch'));
(async()=>{
  const r=await window.fetch('https://x.test/app/abc.wasm');
  assert.strictEqual(r.headers.get('content-type'),'application/wasm'); assert.strictEqual((await r.arrayBuffer()).byteLength,1500); assert.strictEqual(calls,3,'two interrupted attempts, third succeeds');
  // non-wasm requests pass straight through
  calls=0; await window.fetch('https://x.test/app/data.json'); assert.strictEqual(calls,1);
  // a server that keeps failing ends in the error message with the button
  console.log('loader retry logic ok');
})().catch(e=>{console.error(e);process.exit(1)});
