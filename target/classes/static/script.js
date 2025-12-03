// 前端脚本：调用后端 /api/simplify 并显示结果
const inputText = document.getElementById('inputText');
const outputText = document.getElementById('outputText');
const simplifyBtn = document.getElementById('simplifyBtn');
const clearBtn = document.getElementById('clearBtn');
const ratio = document.getElementById('ratio');
const ratioLabel = document.getElementById('ratioLabel');
const dupThreshold = document.getElementById('dupThreshold');
const dupLabel = document.getElementById('dupLabel');
const origChars = document.getElementById('origChars');
const newChars = document.getElementById('newChars');
const reduction = document.getElementById('reduction');
const removedList = document.getElementById('removedList');
const cleanFillers = document.getElementById('cleanFillers');
const dedupe = document.getElementById('dedupe');
const preserveOrder = document.getElementById('preserveOrder');

const fileInput = document.getElementById('fileInput');
const loadFileBtn = document.getElementById('loadFileBtn');
const presetButtons = document.querySelectorAll('.preset');

function updateLabels() {
  const keep = 100 - parseInt(ratio.value);
  ratioLabel.textContent = `目标保留 ${keep}%`;
  dupLabel.textContent = `相似度阈值 ${ (parseInt(dupThreshold.value)/100).toFixed(2) }`;
}
updateLabels();
ratio.addEventListener('input', updateLabels);
dupThreshold.addEventListener('input', updateLabels);

// 文件导入
loadFileBtn.addEventListener('click', ()=>{
  const f = fileInput.files[0];
  if(!f){ alert('请选择一个 .txt 文件'); return; }
  const reader = new FileReader();
  reader.onload = e => {
    inputText.value = e.target.result;
    // 触发一次简化以预览
    simplifyBtn.click();
  };
  reader.readAsText(f, 'utf-8');
});

// 预设按钮
presetButtons.forEach(btn=>{
  btn.addEventListener('click', ()=>{
    presetButtons.forEach(b=>b.classList.remove('active'));
    btn.classList.add('active');
    const v = parseInt(btn.dataset.ratio);
    ratio.value = v;
    updateLabels();
  })
});

// 统计和列表更新
function refreshStats(orig, out){
  origChars.textContent = orig;
  newChars.textContent = out;
  const red = orig === 0 ? 0 : Math.round((orig-out)/orig*100);
  reduction.textContent = red;
}
function updateRemovedList(removed){
  removedList.innerHTML = '';
  if(!removed || removed.length === 0){
    removedList.innerHTML = '<li><em>（无）</em></li>';
    return;
  }
  removed.forEach(s=>{
    const li = document.createElement('li');
    li.textContent = s;
    removedList.appendChild(li);
  })
}

// 调用后端
async function callBackendSimplify(text, opts) {
  const payload = {
    text,
    compressRatio: opts.compressRatio,
    dupThreshold: opts.dupThreshold,
    dedupe: opts.dedupe,
    preserveOrder: opts.preserveOrder,
    cleanFillers: opts.cleanFillers
  };
  const res = await fetch('/api/simplify', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(payload)
  });
  if(!res.ok) {
    const txt = await res.text();
    throw new Error('Server error: ' + txt);
  }
  return await res.json();
}

simplifyBtn.addEventListener('click', async ()=>{
  try{
    const text = inputText.value || '';
    const opts = {
      compressRatio: parseInt(ratio.value),
      dupThreshold: parseInt(dupThreshold.value)/100,
      dedupe: dedupe.checked,
      preserveOrder: preserveOrder.checked,
      cleanFillers: cleanFillers.checked
    };
    const result = await callBackendSimplify(text, opts);
    outputText.value = result.text;
    refreshStats(result.origLen || 0, result.newLen || 0);
    updateRemovedList(result.removed || []);
  }catch(err){
    console.error(err);
    alert('请求失败：' + err.message);
  }
});

clearBtn.addEventListener('click', ()=>{
  inputText.value = '';
  outputText.value = '';
  origChars.textContent = '0';
  newChars.textContent = '0';
  reduction.textContent = '0';
  removedList.innerHTML = '<li><em>（无）</em></li>';
});

// 实时预览节流
let typingTimer = null;
inputText.addEventListener('input', ()=>{
  if(typingTimer) clearTimeout(typingTimer);
  typingTimer = setTimeout(()=>{ simplifyBtn.click(); }, 650);
});

// 初始化
document.addEventListener('DOMContentLoaded', ()=>{
  removedList.innerHTML = '<li><em>（无）</em></li>';
});