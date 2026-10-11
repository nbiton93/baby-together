const {readdirSync,readFileSync}=require('node:fs');const {execFileSync}=require('node:child_process');const path=require('node:path');
const root=path.join(__dirname,'..');for(const file of readdirSync(path.join(root,'shared')).filter(f=>f.endsWith('.js')))execFileSync(process.execPath,['--check',path.join(root,'shared',file)]);
const html=readFileSync(path.join(root,'shared/index.html'),'utf8');if(!html.includes('lang="he" dir="rtl"')||!html.includes("connect-src 'none'"))throw Error('Missing Hebrew or CSP protection');
for(const file of readdirSync(path.join(root,'shared'))){const text=readFileSync(path.join(root,'shared',file),'utf8');if(/chatgpt\.com|openai\.com|chatgpt\.site|api\/tracker/.test(text))throw Error('Unexpected runtime service dependency');}
console.log('JavaScript syntax, RTL root, network CSP and standalone dependency checks passed. Native compilation is a separate gate.');
