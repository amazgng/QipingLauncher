'use strict';
/* Geometry shared by the preview and exported JPEG. No network or dependencies. */
(function(root){
 function limit(value,min,max){return Math.max(min,Math.min(max,Number(value)||0));}
 function rect(width,height,zoom,x,y){
  zoom=limit(zoom,1,3);var scale=Math.max(16/width,9/height)*zoom,w=16/scale,h=9/scale;
  return {x:Math.max(0,width-w)*(limit(x,-100,100)+100)/200,y:Math.max(0,height-h)*(limit(y,-100,100)+100)/200,width:w,height:h};
 }
 function outputSize(crop){var units=Math.max(1,Math.min(120,Math.floor(crop.width/16)));return {width:units*16,height:units*9};}
 var api={rect:rect,limit:limit,outputSize:outputSize};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.QipingCrop=api;
})(typeof window==='undefined'?this:window);
