// Resizable panels logic - 3 panels with 2 resizers
(function() {
  const panelsContainer = document.getElementById('panels');
  if (!panelsContainer) return;

  const panels = Array.from(panelsContainer.querySelectorAll('.panel'));
  const resizers = Array.from(panelsContainer.querySelectorAll('.resizer'));
  if (panels.length < 2 || resizers.length === 0) return;

  let isDragging = false;
  let currentResizer = null;
  let startX = 0;
  let startSizes = [];

  function getSizes() {
    return panels.map(p => p.getBoundingClientRect().width);
  }

  function saveSizes() {
    const sizes = panels.map(p => p.style.flexBasis || p.getBoundingClientRect().width + 'px');
    try { localStorage.setItem('praxic_panel_sizes', JSON.stringify(sizes)); } catch(e) {}
  }

  function loadSizes() {
    try {
      const saved = JSON.parse(localStorage.getItem('praxic_panel_sizes'));
      if (saved && saved.length === panels.length) {
        saved.forEach((size, i) => {
          if (panels[i]) panels[i].style.flex = `0 0 ${size}`;
        });
        return true;
      }
    } catch(e) {}
    return false;
  }

  // Try load
  loadSizes();

  resizers.forEach((resizer, idx) => {
    resizer.addEventListener('mousedown', (e) => {
      isDragging = true;
      currentResizer = idx;
      startX = e.clientX;
      startSizes = getSizes();
      resizer.classList.add('dragging');
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
      e.preventDefault();
    });

    // Touch support
    resizer.addEventListener('touchstart', (e) => {
      isDragging = true;
      currentResizer = idx;
      startX = e.touches[0].clientX;
      startSizes = getSizes();
      resizer.classList.add('dragging');
      e.preventDefault();
    }, { passive: false });
  });

  function onMouseMove(e) {
    if (!isDragging || currentResizer === null) return;
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const dx = clientX - startX;
    const containerWidth = panelsContainer.getBoundingClientRect().width;

    // idx resizer is between panel idx and idx+1
    const leftIdx = currentResizer;
    const rightIdx = currentResizer + 1;
    if (leftIdx < 0 || rightIdx >= panels.length) return;

    let leftSize = startSizes[leftIdx] + dx;
    let rightSize = startSizes[rightIdx] - dx;

    const minSize = 180;
    if (leftSize < minSize) {
      rightSize -= (minSize - leftSize);
      leftSize = minSize;
    }
    if (rightSize < minSize) {
      leftSize -= (minSize - rightSize);
      rightSize = minSize;
    }

    // Apply as flex-basis percentages for responsiveness
    const leftPct = (leftSize / containerWidth) * 100;
    const rightPct = (rightSize / containerWidth) * 100;

    panels[leftIdx].style.flex = `0 0 ${leftPct}%`;
    panels[rightIdx].style.flex = `0 0 ${rightPct}%`;
  }

  function onMouseUp() {
    if (!isDragging) return;
    isDragging = false;
    if (currentResizer !== null && resizers[currentResizer]) {
      resizers[currentResizer].classList.remove('dragging');
    }
    currentResizer = null;
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    saveSizes();
  }

  document.addEventListener('mousemove', onMouseMove);
  document.addEventListener('mouseup', onMouseUp);
  document.addEventListener('touchmove', onMouseMove, { passive: false });
  document.addEventListener('touchend', onMouseUp);

  // Also handle vertical resize on mobile (when panels are column)
  function checkOrientation() {
    const isColumn = window.innerWidth <= 900;
    resizers.forEach(r => {
      r.style.cursor = isColumn ? 'row-resize' : 'col-resize';
    });
  }
  window.addEventListener('resize', checkOrientation);
  checkOrientation();
})();
