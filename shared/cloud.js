(function(root,factory){const api=factory(typeof module==='object'?require('./core.js'):root.BTCore);if(typeof module==='object')module.exports=api;else root.BTCloud=api;})(globalThis,function(C){
'use strict';const fileId=/^[A-Za-z0-9_-]{10,200}$/;
function path(id){C.check(fileId.test(id),'מזהה קובץ Drive אינו תקין');return '/drive/v3/files/'+id;}
function transport(call){return async function(method,p,body,query={}){const r=await call('drive',{method,path:p,query,body:body===undefined?null:JSON.stringify(body),contentType:'application/json; charset=UTF-8'});if(r.status<200||r.status>=300){const e=new Error(r.status===401?'נדרש חיבור מחדש ל־Google':r.status===403?'Google לא אישר גישה לקובץ. בדקו את החשבון והרשאות השיתוף':r.status===404?'קובץ היומן לא נמצא ב־Drive. ייתכן שהגישה הוסרה':r.status===429?'Drive עמוס כרגע. ננסה שוב מאוחר יותר':'לא ניתן לסנכרן כעת. הרישומים נשארו בטלפון');e.status=r.status;throw e;}return r.text?JSON.parse(r.text):{};};}
class Sync{
 constructor({call,getState,change}){this.call=call;this.getState=getState;this.change=change;this.api=transport(call);this.busy=null;}
 run(interactive=false){if(this.busy)return this.busy;this.busy=this.perform(interactive).finally(()=>this.busy=null);return this.busy;}
 async perform(interactive){
  let s=this.getState();if(!s.cloud.enabled)return;await this.call('authorize',{interactive,picker:false});
  if(s.cloud.peerId){const m=await this.api('GET',path(s.cloud.peerId),undefined,{fields:'id,mimeType,size,trashed'});C.check(!m.trashed&&Number(m.size||0)<=8*1024*1024,'קובץ היומן אינו זמין או גדול מדי');const doc=await this.api('GET',path(s.cloud.peerId),undefined,{alt:'media'}),peer=C.parseLog(JSON.stringify(doc),s.cloud.peerDevice);await this.change(n=>{n.peer=C.merge(n.peer,peer.operations);});}
  s=this.getState();if(!s.cloud.fileId){const ids=await this.api('GET','/drive/v3/files/generateIds',undefined,{count:'1',space:'drive'});C.check(fileId.test(ids.ids?.[0]),'Drive לא החזיר מזהה קובץ');await this.change(n=>{n.cloud.fileId=ids.ids[0];});}
  s=this.getState();if(!s.cloud.fileCreated){
   try{await this.api('POST','/drive/v3/files',{id:s.cloud.fileId,name:'Baby Together '+s.device.slice(0,8)+'.json',mimeType:'application/json',appProperties:{app:'baby-together',device:s.device}});}catch(e){if(e.status!==409)throw e;const m=await this.api('GET',path(s.cloud.fileId),undefined,{fields:'appProperties,capabilities'});C.check(m.appProperties?.device===s.device&&m.capabilities?.canEdit,'קובץ היומן אינו שייך למכשיר הזה');}
   await this.change(n=>{n.cloud.fileCreated=true;});
  }
  // Only this installation writes this file. Peers receive read-only access.
  s=this.getState();const count=s.own.length;if(count!==s.cloud.uploadedCount||!s.cloud.lastSync){const m=await this.api('GET',path(s.cloud.fileId),undefined,{fields:'appProperties,capabilities'});C.check(m.appProperties?.device===s.device&&m.capabilities?.canEdit,'קובץ היומן אינו שייך למכשיר הזה');await this.api('PATCH','/upload'+path(s.cloud.fileId),C.logDocument(s),{uploadType:'media'});await this.change(n=>{n.cloud.uploadedCount=count;});}
  await this.change(n=>{n.cloud.lastSync=new Date().toISOString();});
 }
 async pickPeer(){const auth=await this.call('authorize',{interactive:true,picker:true}),id=auth.pickedFileIds?.[0];C.check(id,'לא נבחר קובץ');C.check(id!==this.getState().cloud.fileId,'זה היומן של המכשיר הזה');const m=await this.api('GET',path(id),undefined,{fields:'id,size,mimeType'});C.check(Number(m.size||0)<=8*1024*1024,'קובץ היומן גדול מדי');const peer=C.parseLog(JSON.stringify(await this.api('GET',path(id),undefined,{alt:'media'})));C.check(peer.device!==this.getState().device,'זה עותק של היומן שלך');return {id,peer};}
 async share(email){C.check(/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)&&email.length<=254,'כתובת המייל אינה תקינה');await this.run(true);const s=this.getState();C.check(s.cloud.fileCreated,'יש להתחבר ל־Drive לפני השיתוף');C.check(!s.cloud.permissionId,'כבר מוגדר שיתוף. יש להסיר אותו לפני החלפת הכתובת');const p=await this.api('POST',path(s.cloud.fileId)+'/permissions',{type:'user',role:'reader',emailAddress:email},{sendNotificationEmail:'false',fields:'id'});await this.change(n=>{n.cloud.permissionId=p.id;n.cloud.email=email;});}
 async unshare(){const s=this.getState();if(!s.cloud.permissionId)return;await this.call('authorize',{interactive:true,picker:false});C.check(/^[A-Za-z0-9_-]+$/.test(s.cloud.permissionId),'מזהה הרשאה אינו תקין');try{await this.api('DELETE',path(s.cloud.fileId)+'/permissions/'+s.cloud.permissionId);}catch(e){if(e.status!==404)throw e;}await this.change(n=>{n.cloud.permissionId='';n.cloud.email='';});}
}
return {Sync,transport};
});
