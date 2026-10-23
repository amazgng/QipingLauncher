'use strict';
const assert=require('assert'),crop=require('../app/src/main/assets/phone-wallpaper/crop');let count=0;
for(const [w,h] of [[1920,1080],[1080,1920],[4000,1000],[1,1],[16000,100],[23,191],[2048,2048]])for(const zoom of [1,1.37,2,3])for(const x of [-100,-30,0,50,100])for(const y of [-100,0,100]){
 const r=crop.rect(w,h,zoom,x,y),s=crop.outputSize(r);assert(r.x>=-1e-8&&r.y>=-1e-8&&r.x+r.width<=w+1e-8&&r.y+r.height<=h+1e-8);assert(Math.abs(r.width/r.height-16/9)<1e-8);assert(s.width<=1920&&s.height<=1080&&s.width/s.height===16/9);count++;
}
let r=crop.rect(1080,1920,1,0,-100);assert(r.y===0);r=crop.rect(1080,1920,1,0,100);assert(Math.abs(r.y+r.height-1920)<1e-8);r=crop.rect(1920,1080,1,0,0);assert(r.x===0&&r.y===0&&r.width===1920);console.log('PASS crop geometry '+(count+3)+' checks');
