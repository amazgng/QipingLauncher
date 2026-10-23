'use strict';
(function(){
 var $=function(id){return document.getElementById(id);},token=location.hash.slice(1),selected=null,source=null,previewUrl=null,loadingImage=null,revision=0;
 var busy=false,blocked=false,closed=false,timer=null,request=null,failures=0,initialized=false,lastState='',drag=null,pageHidden=false,polling=false;
 var canvas=$('preview'),context=canvas.getContext('2d');
 if(!/^[a-f0-9]{32}$/.test(token))token='';
 if(location.hash)history.replaceState(null,'',location.pathname);
 function error(message){$('error').textContent=message||'';$('error').hidden=!message;}
 function locked(){return closed||busy||blocked;}
 function updateControls(){var lock=locked();$('send').disabled=lock||!selected||!source;$('scene').disabled=lock;$('file').disabled=lock;['zoom','panX','panY','resetCrop'].forEach(function(id){$(id).disabled=lock||!source;});canvas.setAttribute('aria-disabled',String(lock||!source));}
 function releaseLoad(){if(loadingImage){loadingImage.onload=loadingImage.onerror=null;loadingImage.src='';loadingImage=null;}if(previewUrl){URL.revokeObjectURL(previewUrl);previewUrl=null;}}
 function clearImage(){revision++;releaseLoad();selected=null;if(source){source.width=source.height=1;source=null;}drag=null;context.clearRect(0,0,canvas.width,canvas.height);$('cropEditor').hidden=true;$('fileLabel').classList.remove('compact');$('chooseLabel').textContent='选择一张图片';$('filename').textContent='横向或竖向照片都可以调整构图';$('file').value='';updateControls();}
 function showPair(){token='';clearTimeout(timer);$('pairing').hidden=false;$('editor').hidden=true;$('connection').textContent='等待配对';$('status').textContent='查看电视上的配对码';}
 function finish(message){closed=true;clearTimeout(timer);if(request)request.abort();clearImage();$('connection').textContent='连接已结束';$('status').textContent=message||'请在电视上重新开启，再扫码连接';updateControls();}
 function api(path,method,body,headers){var options={method:method||'GET',cache:'no-store',credentials:'omit',headers:headers||{}};if(token)options.headers['X-Qiping-Token']=token;if(body!==undefined)options.body=body;return fetch(path,options).then(function(response){return response.json().then(function(data){if(!response.ok){var e=new Error(data.message||'操作未完成');e.status=response.status;throw e;}return data;});});}
 function applyStatus(data){
  if(closed)return;failures=0;$('pairing').hidden=true;$('editor').hidden=false;$('connection').textContent='已连接 · '+Math.ceil(data.remaining/60)+' 分钟';
  if(!initialized){$('scene').value=String(data.defaultScene);initialized=true;}
  blocked=data.state==='receiving'||data.state==='pending'||data.state==='applying';$('status').textContent=data.message;
  if(data.state==='closed'||data.remaining<=0){finish(data.message);return;}
  if(data.state==='applied'&&lastState!=='applied'){clearImage();$('send').textContent='选择下一张图片';}
  else if(!busy&&data.state!=='applied')$('send').textContent='上传到电视';lastState=data.state;updateControls();
 }
 function poll(){if(closed||pageHidden||polling||!token)return;polling=true;api('/status').then(function(data){polling=false;if(pageHidden||closed)return;applyStatus(data);if(!closed)timer=setTimeout(poll,1800);}).catch(function(e){polling=false;if(closed||pageHidden)return;if(e.status===401){showPair();return;}if(e.status===410){finish(e.message);return;}failures++;if(failures>=3){finish('无法连接电视，请重新开启传输页面后扫码');return;}$('status').textContent='连接暂时中断，正在重试…';timer=setTimeout(poll,3000);});}
 $('pair').onclick=function(){var code=$('code').value.trim();if(!/^[0-9]{6}$/.test(code)){error('请输入 6 位数字配对码');return;}$('pair').disabled=true;error('');api('/pair','POST',code,{'Content-Type':'text/plain'}).then(function(data){token=data.token;closed=false;clearTimeout(timer);poll();}).catch(function(e){error(e.message);}).then(function(){$('pair').disabled=false;});};
 function crop(){return QipingCrop.rect(source.width,source.height,$('zoom').value,$('panX').value,$('panY').value);}
 function paint(target,rect){var ctx=target.getContext('2d');ctx.fillStyle='#111820';ctx.fillRect(0,0,target.width,target.height);ctx.drawImage(source,rect.x,rect.y,rect.width,rect.height,0,0,target.width,target.height);}
 function draw(){if(!source)return;var r=crop();paint(canvas,r);$('zoomValue').textContent=Number($('zoom').value).toFixed(2)+'×';var size=QipingCrop.outputSize(r);$('cropQuality').textContent='上传画面 '+size.width+' × '+size.height+(size.width<1280?' · 原图较小或放大较多，电视上可能不够清晰':' · 已按电视比例裁剪');}
 ['zoom','panX','panY'].forEach(function(id){$(id).oninput=draw;});
 $('resetCrop').onclick=function(){if(locked()||!source)return;$('zoom').value='1';$('panX').value=$('panY').value='0';draw();};
 $('file').onchange=function(){if(locked())return;var file=this.files&&this.files[0];clearImage();error('');if(!file)return;
  if(file.size===0||file.size>20*1024*1024){error('请选择非空、且不超过 20 MB 的图片');return;}
  if(file.type&&!/^image\/(jpeg|png|webp)$/.test(file.type)){error('请先将图片保存为 JPG、PNG 或 WebP 格式');return;}
  var attempt=revision,img=new Image();loadingImage=img;previewUrl=URL.createObjectURL(file);$('filename').textContent='正在读取图片…';
  img.onload=function(){if(attempt!==revision||closed)return;
   try{
    var w=img.naturalWidth,h=img.naturalHeight;if(!w||!h||w*h>48000000||Math.max(w,h)>16000)throw new Error('图片尺寸过大，请先在手机上缩小后选择');
    var factor=Math.min(1,2560/Math.max(w,h));source=document.createElement('canvas');source.width=Math.max(1,Math.round(w*factor));source.height=Math.max(1,Math.round(h*factor));source.getContext('2d').drawImage(img,0,0,source.width,source.height);
    selected=true;$('zoom').value='1';$('panX').value=$('panY').value='0';$('filename').textContent=file.name+' · '+w+' × '+h;$('cropEditor').hidden=false;$('fileLabel').classList.add('compact');$('chooseLabel').textContent='更换图片';$('send').textContent='上传到电视';draw();releaseLoad();updateControls();
   }catch(e){clearImage();error(e.message||'图片无法处理，请换一张较小的图片');}
  };
  img.onerror=function(){if(attempt!==revision)return;clearImage();error('无法读取图片，请改用 JPG、PNG 或 WebP 图片');};img.src=previewUrl;
 };
 canvas.onpointerdown=function(e){if(locked()||!source||drag)return;drag={id:e.pointerId,x:e.clientX,y:e.clientY,px:Number($('panX').value),py:Number($('panY').value),r:crop()};canvas.setPointerCapture(e.pointerId);e.preventDefault();};
 canvas.onpointermove=function(e){if(!drag||drag.id!==e.pointerId||locked()||!source)return;var box=canvas.getBoundingClientRect(),r=drag.r,dx=source.width-r.width,dy=source.height-r.height;
  $('panX').value=QipingCrop.limit(drag.px-(dx>0?(e.clientX-drag.x)*r.width/box.width*200/dx:0),-100,100);$('panY').value=QipingCrop.limit(drag.py-(dy>0?(e.clientY-drag.y)*r.height/box.height*200/dy:0),-100,100);draw();};
 canvas.onpointerup=canvas.onpointercancel=canvas.onlostpointercapture=function(e){if(drag&&drag.id===e.pointerId)drag=null;};
 canvas.onkeydown=function(e){if(locked()||!source)return;var id=e.key==='ArrowLeft'||e.key==='ArrowRight'?'panX':e.key==='ArrowUp'||e.key==='ArrowDown'?'panY':null;if(id){e.preventDefault();$(id).value=Number($(id).value)+(e.key==='ArrowLeft'||e.key==='ArrowUp'?-4:4);draw();}};
 function upload(blob,slot){
  var xhr=new XMLHttpRequest();request=xhr;xhr.open('POST','/upload?scene='+slot);xhr.setRequestHeader('X-Qiping-Token',token);xhr.setRequestHeader('Content-Type','image/jpeg');xhr.timeout=100000;
  xhr.upload.onprogress=function(e){if(e.lengthComputable){$('progress').value=Math.round(e.loaded/e.total*100);$('send').textContent=$('progress').value===100?'电视正在处理…':'正在上传 '+$('progress').value+'%';}};
  xhr.onload=function(){busy=false;request=null;var data;try{data=JSON.parse(xhr.responseText);}catch(e){data={message:'电视响应异常，请重新连接'};}if(xhr.status===200){applyStatus(data);error('');}else{error(data.message);if(xhr.status===401)showPair();if(xhr.status===410)finish(data.message);}updateControls();};
  function failed(message){busy=false;request=null;if(!closed){error(message);$('send').textContent='重新上传';}updateControls();}
  xhr.onerror=function(){failed('上传中断，请检查 Wi-Fi 后重试');};xhr.ontimeout=function(){failed('上传超时，请换一张较小的图片后重试');};xhr.onabort=function(){failed('上传已停止');};xhr.send(blob);
 }
 $('send').onclick=function(){if(!selected||!source||locked())return;busy=true;drag=null;error('');updateControls();$('progress').hidden=false;$('progress').value=0;$('status').textContent='正在准备裁剪后的图片…';
  var attempt=revision,slot=$('scene').value,r=crop(),size=QipingCrop.outputSize(r),output=document.createElement('canvas');output.width=size.width;output.height=size.height;
  try{paint(output,r);output.toBlob(function(blob){output.width=output.height=1;if(closed||attempt!==revision){busy=false;updateControls();return;}if(!blob||blob.size>20*1024*1024){busy=false;error('图片处理失败，请缩小后重试');updateControls();return;}$('status').textContent='正在上传，请保持电视页面打开…';upload(blob,slot);},'image/jpeg',.91);}catch(e){output.width=output.height=1;busy=false;error('浏览器无法生成图片，请换用手机系统浏览器');updateControls();}
 };
 window.addEventListener('pagehide',function(e){pageHidden=true;revision++;clearTimeout(timer);if(request)request.abort();busy=false;releaseLoad();if(!e.persisted)clearImage();});
 window.addEventListener('pageshow',function(e){pageHidden=false;if(e.persisted&&!closed&&token){clearTimeout(timer);updateControls();poll();}});
 if(token)poll();else showPair();
})();
