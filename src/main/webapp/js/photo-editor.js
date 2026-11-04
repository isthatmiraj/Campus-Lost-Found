(function () {
  "use strict";
  var input = document.querySelector('input[type="file"][name="photo"]');
  if (!input) return;

  var MAX_FILE_SIZE = 5 * 1024 * 1024;
  var image = null, originalFile = null, objectUrl = null;
  var rotation = 0, crop = null, mode = null, pointerStart = null;
  var modal, canvas, ctx, status;

  function build() {
    modal = document.createElement("div");
    modal.className = "cf-photo-editor";
    modal.hidden = true;
    modal.innerHTML =
      '<div class="cf-photo-editor__dialog" role="dialog" aria-modal="true" aria-labelledby="cf-photo-editor-title">' +
      '<div class="cf-photo-editor__header"><div><p class="eyebrow">PHOTO EDITOR</p><h2 id="cf-photo-editor-title">Crop your photo</h2></div><button type="button" class="cf-photo-editor__close" data-action="cancel" aria-label="Close">×</button></div>' +
      '<p class="cf-photo-editor__hint">Adjust the square crop frame using its corners. The image itself stays still.</p>' +
      '<div class="cf-photo-editor__stage cf-crop-stage"><canvas width="600" height="600" aria-label="Square crop selection"></canvas></div>' +
      '<div class="cf-photo-editor__tools"><button type="button" class="btn btn-light" data-action="left">↶ Rotate left</button><button type="button" class="btn btn-light" data-action="right">↷ Rotate right</button></div>' +
      '<p class="cf-photo-editor__status" aria-live="polite"></p>' +
      '<div class="cf-photo-editor__footer"><button type="button" class="btn btn-light" data-action="cancel">Cancel</button><button type="button" class="btn" data-action="apply">Use this crop</button></div></div>';
    document.body.appendChild(modal);
    canvas = modal.querySelector("canvas");
    ctx = canvas.getContext("2d");
    status = modal.querySelector(".cf-photo-editor__status");

    modal.addEventListener("click", function (e) {
      var b = e.target.closest("[data-action]");
      if (!b) { if (e.target === modal) cancel(); return; }
      var a = b.dataset.action;
      if (a === "cancel") cancel();
      if (a === "left") { rotation = (rotation + 3) % 4; initCrop(); render(); }
      if (a === "right") { rotation = (rotation + 1) % 4; initCrop(); render(); }
      if (a === "apply") apply();
    });

    canvas.addEventListener("pointerdown", function (e) {
      var p = point(e);
      var handle = hitHandle(p);
      if (handle) {
        mode = handle;
      } else if (p.x >= crop.x && p.x <= crop.x + crop.size && p.y >= crop.y && p.y <= crop.y + crop.size) {
        mode = "move";
      } else {
        return;
      }
      pointerStart = { x: p.x, y: p.y, crop: Object.assign({}, crop) };
      canvas.classList.add("is-dragging");
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener("pointermove", function (e) {
      if (!mode || !pointerStart) return;
      var p = point(e), dx = p.x - pointerStart.x, dy = p.y - pointerStart.y;
      var c = pointerStart.crop;
      var bounds = imageBounds();
      if (mode === "move") {
        crop = {
          x: Math.max(bounds.x, Math.min(bounds.x + bounds.w - c.size, c.x + dx)),
          y: Math.max(bounds.y, Math.min(bounds.y + bounds.h - c.size, c.y + dy)),
          size: c.size
        };
      } else {
        var maxCrop = Math.min(bounds.w, bounds.h);
        var size = Math.max(Math.min(100, maxCrop), Math.min(maxCrop, c.size + (mode.includes("b") ? dy : -dy) / 2 + (mode.includes("r") ? dx : -dx) / 2));
        var nextX = c.x + (mode.includes("l") ? c.size - size : 0);
        var nextY = c.y + (mode.includes("t") ? c.size - size : 0);
        crop = {
          x: Math.max(bounds.x, Math.min(bounds.x + bounds.w - size, nextX)),
          y: Math.max(bounds.y, Math.min(bounds.y + bounds.h - size, nextY)),
          size: size
        };
      }
      render();
    });
    ["pointerup", "pointercancel", "lostpointercapture"].forEach(function (name) {
      canvas.addEventListener(name, function () { mode = null; pointerStart = null; canvas.classList.remove("is-dragging"); });
    });
    document.addEventListener("keydown", function (e) { if (!modal.hidden && e.key === "Escape") cancel(); });
  }

  function point(e) {
    var r = canvas.getBoundingClientRect();
    return { x: (e.clientX - r.left) * 600 / r.width, y: (e.clientY - r.top) * 600 / r.height };
  }
  function hitHandle(p) {
    if (!crop) return null;
    var c = crop, r = 24;
    if (Math.hypot(p.x - c.x, p.y - c.y) < r) return "tl";
    if (Math.hypot(p.x - c.x - c.size, p.y - c.y) < r) return "tr";
    if (Math.hypot(p.x - c.x, p.y - c.y - c.size) < r) return "bl";
    if (Math.hypot(p.x - c.x - c.size, p.y - c.y - c.size) < r) return "br";
    return null;
  }
  function dimensions() {
    var swap = rotation % 2 === 1;
    return { w: swap ? image.naturalHeight : image.naturalWidth, h: swap ? image.naturalWidth : image.naturalHeight };
  }
  function imageBounds() {
    var d = dimensions();
    var scale = Math.min(600 / d.w, 600 / d.h);
    var w = d.w * scale, h = d.h * scale;
    return { x: (600 - w) / 2, y: (600 - h) / 2, w: w, h: h };
  }
  function drawImage(c, targetSize) {
    var d = dimensions();
    var scale = Math.min(targetSize / d.w, targetSize / d.h);
    c.save();
    c.translate(targetSize / 2, targetSize / 2);
    c.rotate(rotation * Math.PI / 2);
    c.scale(scale, scale);
    c.drawImage(image, -image.naturalWidth / 2, -image.naturalHeight / 2);
    c.restore();
    return { scale: scale, width: d.w * scale, height: d.h * scale, x: (targetSize - d.w * scale) / 2, y: (targetSize - d.h * scale) / 2 };
  }
  function initCrop() {
    var d = dimensions();
    var side = Math.min(600, d.w / Math.max(d.w, d.h) * 600, d.h / Math.max(d.w, d.h) * 600);
    // A centered square crop that fits fully inside the displayed image.
    side = Math.min(540, 600 * Math.min(d.w, d.h) / Math.max(d.w, d.h));
    crop = { x: (600 - side) / 2, y: (600 - side) / 2, size: side };
  }
  function render() {
    if (!image) return;
    ctx.clearRect(0, 0, 600, 600);
    ctx.fillStyle = "#080b09";
    ctx.fillRect(0, 0, 600, 600);
    var geom = drawImage(ctx, 600);
    var c = crop;
    // Dim everything outside the square selection.
    ctx.fillStyle = "rgba(0,0,0,.62)";
    ctx.fillRect(0, 0, 600, 600);
    ctx.save();
    ctx.beginPath();
    ctx.rect(c.x, c.y, c.size, c.size);
    ctx.clip();
    drawImage(ctx, 600);
    ctx.restore();
    // Thirds grid and crop border.
    ctx.save();
    ctx.strokeStyle = "rgba(255,255,255,.88)";
    ctx.lineWidth = 1;
    for (var i = 1; i <= 2; i++) {
      var t = c.size * i / 3;
      ctx.beginPath(); ctx.moveTo(c.x + t, c.y); ctx.lineTo(c.x + t, c.y + c.size); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(c.x, c.y + t); ctx.lineTo(c.x + c.size, c.y + t); ctx.stroke();
    }
    ctx.lineWidth = 2;
    ctx.strokeRect(c.x, c.y, c.size, c.size);
    [[c.x,c.y],[c.x+c.size,c.y],[c.x,c.y+c.size],[c.x+c.size,c.y+c.size]].forEach(function (p) {
      ctx.fillStyle = "#fff"; ctx.fillRect(p[0]-6,p[1]-6,12,12);
    });
    ctx.restore();
    status.textContent = "Drag inside the square to move it · drag a corner to resize";
  }
  function open(file) {
    originalFile = file;
    rotation = 0;
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = URL.createObjectURL(file);
    image = new Image();
    image.onload = function () {
      initCrop();
      modal.hidden = false;
      document.body.classList.add("cf-photo-editor-open");
      render();
      modal.querySelector('[data-action="apply"]').focus();
    };
    image.onerror = function () { alert("Could not open this image."); input.value = ""; };
    image.src = objectUrl;
  }
  function cancel() {
    modal.hidden = true;
    document.body.classList.remove("cf-photo-editor-open");
    input.value = "";
    cleanup();
  }
  function cleanup() {
    image = null; originalFile = null; crop = null;
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = null;
  }
  function apply() {
    if (!image || !crop) return;
    var d = dimensions();
    var scale = Math.min(600 / d.w, 600 / d.h);
    var displayedW = d.w * scale, displayedH = d.h * scale;
    var ox = (600 - displayedW) / 2, oy = (600 - displayedH) / 2;
    var sx = (crop.x - ox) / scale, sy = (crop.y - oy) / scale, sw = crop.size / scale;
    sx = Math.max(0, Math.min(d.w - sw, sx)); sy = Math.max(0, Math.min(d.h - sw, sy));
    var full = document.createElement("canvas");
    full.width = d.w; full.height = d.h;
    var fctx = full.getContext("2d");
    fctx.translate(d.w / 2, d.h / 2); fctx.rotate(rotation * Math.PI / 2);
    fctx.drawImage(image, -image.naturalWidth / 2, -image.naturalHeight / 2);
    var out = document.createElement("canvas");
    out.width = 1080; out.height = 1080;
    out.getContext("2d").drawImage(full, sx, sy, sw, sw, 0, 0, 1080, 1080);
    out.toBlob(function (blob) {
      if (!blob) { alert("Could not crop this photo."); return; }
      var edited = new File([blob], (originalFile.name.replace(/\.[^.]+$/, "") || "campusfind-photo") + "-crop.jpg", { type: "image/jpeg", lastModified: Date.now() });
      var dt = new DataTransfer(); dt.items.add(edited); input.files = dt.files;
      modal.hidden = true; document.body.classList.remove("cf-photo-editor-open");
      var old = document.getElementById("photo-preview"); if (old) old.remove();
      var img = document.createElement("img");
      img.id = "photo-preview";
      img.alt = "Cropped photo preview";
      img.src = URL.createObjectURL(edited);
      img.className = "cf-photo-final-preview";
      // Apply hard limits inline as well, so broad site-wide img rules cannot
      // make the generated square preview expand beyond the form.
      img.style.setProperty("display", "block", "important");
      img.style.setProperty("width", "min(100%, 280px)", "important");
      img.style.setProperty("max-width", "100%", "important");
      img.style.setProperty("height", "auto", "important");
      img.style.setProperty("max-height", "280px", "important");
      img.style.setProperty("object-fit", "contain", "important");
      img.style.setProperty("box-sizing", "border-box", "important");
      img.style.setProperty("margin", "12px 0", "important");
      input.insertAdjacentElement("afterend", img);
      cleanup();
    }, "image/jpeg", 0.9);
  }

  build();
  input.addEventListener("change", function () {
    var file = input.files && input.files[0];
    var old = document.getElementById("photo-preview"); if (old) old.remove();
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type)) { input.value = ""; alert("Choose a JPG or PNG image."); return; }
    if (file.size > MAX_FILE_SIZE) { input.value = ""; alert("Image must be 5 MB or smaller."); return; }
    open(file);
  });
})();
