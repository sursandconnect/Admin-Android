
(function(){
const META={
businesses:{n:['Business Name','Name'],s:['Business Category','Category'],i:'🏪'},services:{n:['Service Person Name','Provider Name','Name'],s:['Service Type','Category'],i:'🛠️'},healthcare:{n:['Facility Name','Hospital Name','Clinic Name','Name'],s:['Facility Type','Type'],i:'🏥'},doctors:{n:['Doctor Name','Name'],s:['Speciality','Degree'],i:'👨‍⚕️'},education:{n:['Institution Name','School Name','Name'],s:['Institution Type','Type'],i:'🎓'},transport:{n:['Vehicle Name','Bus Name','Vehicle Type','Route Name'],s:['Route Name','Destination','End Point'],i:'🚌'},places:{n:['Place Name','Name'],s:['Category','Place Type'],i:'📍'},governmentOffices:{n:['Office Name','Name'],s:['Department','Office Type'],i:'🏛️'},events:{n:['Event Name','Title','Name'],s:['Venue','Event Date','Start Date'],i:'📅'},notifications:{n:['Title','Notification Title','Name'],s:['Date','Status'],i:'🔔'},representatives:{n:['Name','Representative Name'],s:['Designation','Ward'],i:'👥'},cityConnect:{n:['Group Name','Name','Title'],s:['Ward','Link'],i:'💬'},changeMakers:{n:['Name','Member Name'],s:['Occupation','Ward'],i:'✨'},agriculture:{n:['Title','Name'],s:['Category','Type'],i:'🌾'},usefulLinks:{n:['Title','Name'],s:['Category','URL'],i:'🌐'},governmentServices:{n:['Service Name','Name'],s:['Department','Category'],i:'🏢'},importantContacts:{n:['Name','Service Name'],s:['Category','Contact Number'],i:'☎️'},pathology:{n:['Patient Name','Name'],s:['Test Requirement','Status'],i:'🧪'},medicineOrders:{n:['Customer Name','Patient Name','Name'],s:['Medicine Names','Status'],i:'💊'},jobs:{n:['Job Title','Title'],s:['Employer Name','Job Type'],i:'💼'},localAds:{n:['Title','Advertisement Title','Name'],s:['Priority','Status'],i:'📣'},contacts:{n:['Name','Subject'],s:['Subject','Mobile'],i:'✉️'},adminNotifications:{n:['Title','Request Type'],s:['Request Type','Status'],i:'📥'}};
function first(r,a){for(const k of a||[])if(r&&r[k]!=null&&String(r[k]).trim())return String(r[k]);return ''}
function meta(){return META[section]||{n:['Name','Title'],s:['Category','Status'],i:'📄'}}
function titleOf(r){return first(r,meta().n)||first(r,['Name','Title','ID'])||'Record'}
function subOf(r){return first(r,meta().s)}
function imageOf(r){return typeof scRecordImage==='function'?scRecordImage(r):''}
function keysOf(r){return Object.keys(r||{}).filter(k=>!String(k).startsWith('_')&&!['Password Salt','Password Hash'].includes(k))}
window.scCardDecision=function(i,op){detailModal.classList.remove('active');decide(i,op)}
window.scCardEdit=function(i){detailModal.classList.remove('active');openForm(i)}
window.scCardDelete=function(i){detailModal.classList.remove('active');removeRow(i)}
window.openRecord=function(i){
 const r=rows[i];if(!r)return;const mm=meta(),name=titleOf(r),id=idOf(r)||'No ID',img=imageOf(r);
 detailTitle.textContent=name;
 const hero=`<div class="detailHero">${img?`<img src="${esc(img)}" alt="">`:`<div class="ph">${mm.i}</div>`}<div><h2>${esc(name)}</h2><p>${esc(id)}</p>${subOf(r)?`<p>${esc(subOf(r))}</p>`:''}</div></div>`;
 const fields=keysOf(r).map(k=>`<div class="detail"><small>${esc(k)}</small><b>${esc(adminDisplayValue(k,r[k]))}</b></div>`).join('');
 let a='<div class="detailActions">';const pending=isPending(r);
 if(pending)a+=`<button class="small green" onclick="scCardDecision(${i},'approve')">Approve</button><button class="small orange" onclick="scCardDecision(${i},'reject')">Reject</button>`;
 if(section==='changeMakers'&&!pending)a+=`<button class="small orange" onclick="notifyCity(${i})">Notify City Connect</button>`;
 if(section==='services')a+=`<button class="small green" onclick="chooseQrLanguage(${i},'service')">Digital Card</button>`;
 else if(typeof qrSection==='function'&&qrSection(section))a+=`<button class="small green" onclick="chooseQrLanguage(${i},'sticker')">QR Sticker</button>`;
 a+=`<button class="small blue" onclick="scCardEdit(${i})">Edit</button><button class="small red" onclick="scCardDelete(${i})">Delete</button></div>`;
 detailBody.innerHTML=hero+`<div class="detailgrid">${fields}</div>`+a;detailModal.classList.add('active')
};
render=function(){
 if(!rows.length){content.className='empty';content.textContent='No records found.';return}
 const mm=meta();content.className='';content.innerHTML='<div class="recordgrid">'+rows.map((r,i)=>`<article class="recordcard ${isPending(r)?'pending':''}" onclick="openRecord(${i})"><div>${imageOf(r)?`<img src="${esc(imageOf(r))}" alt="" style="width:38px;height:38px;object-fit:cover;border-radius:10px;border:1px solid var(--border)">`:`<div class="rcicon">${mm.i}</div>`}<h3>${esc(titleOf(r))}</h3><div class="rcid">${esc(idOf(r)||'No ID')}</div></div>${subOf(r)?`<div class="rcsub">${esc(subOf(r))}</div>`:''}</article>`).join('')+'</div>';
 if(section==='adminNotifications'&&q.get('requestId')){const rid=q.get('requestId'),n=rows.find(x=>String(x['Request ID']||'')===rid);if(n)openNotificationDetail(n)}
};
function refreshButton(){const h=document.querySelector('.headbtns');if(!h||document.getElementById('scAdminHeaderRefresh'))return;const b=document.createElement('button');b.type='button';b.id='scAdminHeaderRefresh';b.className='iconbtn scAdminRefresh';b.title='Refresh';b.setAttribute('aria-label','Refresh');b.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M20 6v5h-5"/><path d="M20 11a8 8 0 1 0 1 4"/></svg>';b.onclick=async()=>{if(b.dataset.busy)return;b.dataset.busy='1';b.classList.add('refreshing');try{await loadData()}finally{setTimeout(()=>{delete b.dataset.busy;b.classList.remove('refreshing')},300)}};h.insertBefore(b,h.firstChild)}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',refreshButton);else refreshButton();
})();
