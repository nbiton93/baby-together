(function(root,factory){const api=factory();if(typeof module==='object')module.exports=api;else root.BTCore=api;})(globalThis,function(){
'use strict';
const MAX_OPS=20000, uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const details={feed:['formula','milk','left','right','both'],sleep:['sleep'],diaper:['wet','dirty','mixed'],pump:['pump'],weight:['weight'],note:['note']};
function check(ok,message){if(!ok)throw new Error(message);}
function text(v,max){return typeof v==='string'&&v.length<=max;}
function integer(v,min,max){return Number.isInteger(v)&&v>=min&&v<=max;}
function time(v){return typeof v==='string'&&Number.isFinite(Date.parse(v))&&/^\d{4}-\d{2}-\d{2}T/.test(v);}
function validateEvent(v,now=Date.now(),remote=false){
 check(v&&Object.hasOwn(details,v.kind),'סוג הרישום אינו תקין');check(Object.keys(v).every(k=>['kind','detail','amount','minutes','started','ended','note'].includes(k)),'שדה לא מוכר ברישום');check(details[v.kind].includes(v.detail),'פרטי הרישום אינם תקינים');
 check(time(v.started),'יש לבחור תאריך ושעה');if(!remote)check(Date.parse(v.started)<=now+60000,'אין לשמור רישום בעתיד');
 check(text(v.note,2000),'הערה יכולה להכיל עד 2,000 תווים');
 if(v.kind==='sleep'){check(v.ended===null||(time(v.ended)&&Date.parse(v.ended)>=Date.parse(v.started)),'סיום השינה חייב להיות אחרי ההתחלה');if(!remote&&v.ended)check(Date.parse(v.ended)<=now+60000,'סיום השינה אינו יכול להיות בעתיד');}else check(v.ended===null,'זמן סיום אינו מתאים לרישום');
 if(v.kind==='weight')check(integer(v.amount,1,30000),'יש להזין משקל בגרמים');else if(v.kind==='pump'||(v.kind==='feed'&&['formula','milk'].includes(v.detail)))check(integer(v.amount,1,3000),'יש להזין כמות במ״ל');else check(v.amount===null,'כמות אינה מתאימה לרישום');
 if(v.kind==='feed'&&['left','right','both'].includes(v.detail))check(integer(v.minutes,1,1440),'יש להזין משך הנקה בדקות');else check(v.minutes===null,'משך אינו מתאים לרישום');
 if(v.kind==='note')check(v.note.trim().length>0,'יש לכתוב הערה');return v;
}
function validateProfile(v){check(v&&text(v.name,60)&&v.name.trim(),'יש להזין שם עד 60 תווים');check(v.birth===''||(/^\d{4}-\d{2}-\d{2}$/.test(v.birth)&&!isNaN(Date.parse(v.birth))&&new Date(v.birth).toISOString().slice(0,10)===v.birth),'תאריך לידה אינו תקין');}
function validateOp(o){
 check(o&&uuid.test(o.id)&&uuid.test(o.device),'מזהה פעולה אינו תקין');check(integer(o.clock,1,999999999),'מונה יומן אינו תקין');check(o.target==='profile'||uuid.test(o.target),'מזהה רישום אינו תקין');check(o.base===null||uuid.test(o.base),'גרסת רישום אינה תקינה');check(time(o.at)&&text(o.author,60)&&o.author.trim(),'פרטי הכותב אינם תקינים');check(typeof o.deleted==='boolean','פעולת יומן אינה תקינה');if(o.resolves)check(Array.isArray(o.resolves)&&o.resolves.length<=100&&o.resolves.every(x=>uuid.test(x)),'גרסאות לפתרון אינן תקינות');
 if(!o.deleted)o.target==='profile'?validateProfile(o.value):validateEvent(o.value,Date.now(),true);else check(o.value===null&&o.target!=='profile','פעולת מחיקה אינה תקינה');return o;
}
function compare(a,b){return a.clock-b.clock||(a.device<b.device?-1:a.device>b.device?1:0)||(a.id<b.id?-1:a.id>b.id?1:0);}
function merge(...logs){const map=new Map();for(const log of logs){check(Array.isArray(log)&&log.length<=MAX_OPS,'היומן גדול מדי או אינו תקין');for(const o of log){validateOp(o);const old=map.get(o.id);check(!old||JSON.stringify(old)===JSON.stringify(o),'שתי פעולות שונות נושאות אותו מזהה');map.set(o.id,o);}}check(map.size<=MAX_OPS,'היומן הגיע למגבלת הגרסה. יש לייצא גיבוי.');return [...map.values()].sort(compare);}
function materialize(ops){
 const winners=new Map(),versions=new Map();for(const o of merge(ops)){winners.set(o.target,o);const list=versions.get(o.target)||[];list.push(o);versions.set(o.target,list);}
 const events=[],removed=[],conflicts=[];for(const [id,o]of winners){if(id!=='profile')(o.deleted?removed:events).push({id,opId:o.id,author:o.author,...o.value});const list=versions.get(id),referenced=new Set(list.flatMap(x=>[x.base,...(x.resolves||[])])),tips=list.filter(x=>!referenced.has(x.id));if(tips.length>1)conflicts.push({target:id,winner:o.id,tips});}
 events.sort((a,b)=>Date.parse(b.started)-Date.parse(a.started)||a.id.localeCompare(b.id));return {events,removed,profile:winners.get('profile')?.value||{name:'הקטנה שלנו',birth:''},winners,versions,conflicts};
}
function makeOp(s,target,value,deleted,id,now=Date.now()){
 check(uuid.test(id)&&uuid.test(s.device),'מזהה המכשיר אינו תקין');check(text(s.author,60)&&s.author.trim(),'קודם יש להזין את השם שלך בהגדרות');if(!deleted)target==='profile'?validateProfile(value):validateEvent(value,now);
 const all=merge(s.own,s.peer);check(all.length<MAX_OPS,'היומן הגיע למגבלה. יש לייצא גיבוי.');const previous=materialize(all).winners.get(target);return validateOp({id,device:s.device,clock:all.reduce((m,o)=>Math.max(m,o.clock),0)+1,at:new Date(now).toISOString(),target,base:previous?.id||null,author:s.author.trim(),deleted,value:deleted?null:value});
}
function logDocument(s){return {format:'baby-together-journal',version:1,device:s.device,author:s.author,operations:s.own};}
function parseLog(raw,expectedDevice){check(typeof raw==='string'&&raw.length<=8*1024*1024,'קובץ היומן גדול מדי');const v=JSON.parse(raw);check(v.format==='baby-together-journal'&&v.version===1&&uuid.test(v.device)&&text(v.author,60),'זה אינו קובץ יומן של Baby Together');if(expectedDevice)check(v.device===expectedDevice,'קובץ היומן הוחלף במכשיר אחר');v.operations=merge(v.operations);check(v.operations.every(o=>o.device===v.device),'בקובץ נמצאו פעולות ממכשיר אחר');return v;}
function fresh(device){check(uuid.test(device),'מזהה מכשיר אינו תקין');return {version:1,device,author:'',own:[],peer:[],cloud:{enabled:false,fileId:'',fileCreated:false,peerId:'',peerDevice:'',permissionId:'',email:'',uploadedCount:0,lastSync:''}};}
function parseState(raw){const s=JSON.parse(raw);check(s.version===1&&uuid.test(s.device)&&text(s.author,60)&&s.cloud&&typeof s.cloud.enabled==='boolean','הקובץ המקומי אינו תקין. הנתונים לא אופסו.');merge(s.own,s.peer);check(s.own.every(o=>o.device===s.device),'יומן המכשיר אינו תקין');return s;}
function localDate(d=new Date()){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
function summary(events,date,now=Date.now()){
 const start=new Date(date+'T00:00:00').getTime(),next=new Date(date+'T00:00:00');next.setDate(next.getDate()+1);const end=next.getTime(),day=events.filter(e=>Date.parse(e.started)>=start&&Date.parse(e.started)<end);
 const intervals=events.filter(e=>e.kind==='sleep').map(e=>[Math.max(start,Date.parse(e.started)),Math.min(end,e.ended?Date.parse(e.ended):now)]).filter(([a,b])=>b>a).sort((a,b)=>a[0]-b[0]);let sleep=0,tail=0;for(const[a,b]of intervals){sleep+=Math.max(0,b-Math.max(a,tail));tail=Math.max(tail,b);}
 return {feeds:day.filter(e=>e.kind==='feed').length,ml:day.filter(e=>e.kind==='feed'&&e.amount!==null).reduce((n,e)=>n+e.amount,0),diapers:day.filter(e=>e.kind==='diaper').length,sleepMinutes:Math.floor(sleep/60000)};
}
return {MAX_OPS,check,validateEvent,validateProfile,validateOp,merge,materialize,makeOp,logDocument,parseLog,fresh,parseState,localDate,summary};
});
