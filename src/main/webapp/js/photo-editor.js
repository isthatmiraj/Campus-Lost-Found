(function () {
  "use strict";
  var input = document.querySelector('input[type="file"][name="photo"]');
  if (!input) return;

  var MAX_FILE_SIZE = 5 * 1024 * 1024;
  var image = null, originalFile = null, objectUrl = null;
  var rotation = 0, crop = null, mode = null, pointerStart = null;
  var modal, canvas, ctx, status;
  var chooser, chooserImage, chooserFile = null, chooserObjectUrl = null;

  function build() {
    modal = document.createElement("div");
    modal.className = "cf-photo-editor";
    modal.hidden = true;
    modal.innerHTML =
      '<div class="cf-photo-editor__dialog" role="dialog" aria-modal="true" aria-labelledby="cf-photo-editor-title">' +
      '<div class="cf-photo-editor__header"><div><p class="eyebrow">PHOTO EDITOR</p><h2 id="cf-photo-editor-title">Crop your photo</h2></div><button type="button" class="cf-photo-editor__close" data-action="cancel" aria-label="Close">×</button></div>' +
      '<p class="cf-photo-editor__hint">Drag inside the selection to move it. Drag any corner or edge to resize freely.</p>' +
      '<div class="cf-photo-editor__stage cf-crop-stage"><canvas width="600" height="600" aria-label="Freeform crop selection"></canvas></div>' +
      '<div class="cf-photo-editor__tools"><button type="button" class="btn btn-light" data-action="left">↶ Rotate left</button><button type="button" class="btn btn-light" data-action="right">↷ Rotate right</button></div>' +
      '<p class="cf-photo-editor__status" aria-live="polite"></p>' +
      '<div class="cf-photo-editor__footer"><button type="button" class="btn btn-light" data-action="cancel">Cancel</button><button type="button" class="btn" data-action="apply">Use this crop</button></div></div>';
    document.body.appendChild(modal);

    chooser = document.createElement("div");
    chooser.className = "cf-photo-editor cf-photo-choice";
    chooser.hidden = true;
    chooser.innerHTML =
      '<div class="cf-photo-editor__dialog cf-photo-choice__dialog" role="dialog" aria-modal="true" aria-labelledby="cf-photo-choice-title">' +
      '<div class="cf-photo-choice__top"><p class="eyebrow">ADD A PHOTO</p><button type="button" class="cf-photo-choice__close" data-choice="cancel" aria-label="Close photo selection">×</button></div>' +
      '<h2 id="cf-photo-choice-title">How would you like to use this photo?</h2>' +
      '<img class="cf-photo-choice__preview" alt="Selected photo preview">' +
      '<div class="cf-photo-choice__actions">' +
      '<button type="button" class="btn" data-choice="original"><span>Upload as is</span><small>Keep the original photo</small></button>' +
      '<button type="button" class="btn btn-light" data-choice="edit"><span>Edit image</span><small>Crop or rotate before uploading</small></button>' +
      '</div>' +
      '<button type="button" class="cf-photo-choice__cancel" data-choice="cancel">Choose another photo</button>' +
      '</div>';
    document.body.appendChild(chooser);
    chooserImage = chooser.querySelector(".cf-photo-choice__preview");

    chooser.addEventListener("click", function (e) {
      var b = e.target.closest("[data-choice]");
      if (!b) return;
      var choice = b.dataset.choice;
      if (choice === "cancel") clearSelection();
      if (choice === "original") acceptOriginal();
      if (choice === "edit") {
        var file = chooserFile;
        chooser.hidden = true;
        document.body.classList.remove("cf-photo-editor-open");
        if (file) open(file);
      }
    });

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
      } else if (
        p.x >= crop.x && p.x <= crop.x + crop.w &&
        p.y >= crop.y && p.y <= crop.y + crop.h
      ) {
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

      var p = point(e);
      var dx = p.x - pointerStart.x;
      var dy = p.y - pointerStart.y;
      var c = pointerStart.crop;
      var bounds = imageBounds();
      var minSize = 35;

      if (mode === "move") {
        crop = {
          x: Math.max(bounds.x, Math.min(bounds.x + bounds.w - c.w, c.x + dx)),
          y: Math.max(bounds.y, Math.min(bounds.y + bounds.h - c.h, c.y + dy)),
          w: c.w,
          h: c.h
        };
      } else {
        var left = c.x;
        var top = c.y;
        var right = c.x + c.w;
        var bottom = c.y + c.h;

        if (mode.includes("l")) {
          left = Math.max(bounds.x, Math.min(right - minSize, c.x + dx));
        }
        if (mode.includes("r")) {
          right = Math.min(bounds.x + bounds.w, Math.max(left + minSize, c.x + c.w + dx));
        }
        if (mode.includes("t")) {
          top = Math.max(bounds.y, Math.min(bottom - minSize, c.y + dy));
        }
        if (mode.includes("b")) {
          bottom = Math.min(bounds.y + bounds.h, Math.max(top + minSize, c.y + c.h + dy));
        }

        crop = {
          x: left,
          y: top,
          w: right - left,
          h: bottom - top
        };
      }

      render();
    });
    ["pointerup", "pointercancel", "lostpointercapture"].forEach(function (name) {
      canvas.addEventListener(name, function () { mode = null; pointerStart = null; canvas.classList.remove("is-dragging"); });
    });
    document.addEventListener("keydown", function (e) {
      if (e.key !== "Escape") return;
      if (!chooser.hidden) clearSelection();
      else if (!modal.hidden) cancel();
    });
  }

  function showChoice(file) {
    chooserFile = file;
    if (chooserObjectUrl) URL.revokeObjectURL(chooserObjectUrl);
    chooserObjectUrl = URL.createObjectURL(file);
    chooserImage.src = chooserObjectUrl;
    chooser.hidden = false;
    document.body.classList.add("cf-photo-editor-open");
    chooser.querySelector('[data-choice="original"]').focus();
  }

  function acceptOriginal() {
    var file = chooserFile;
    if (!file) return;
    chooser.hidden = true;
    document.body.classList.remove("cf-photo-editor-open");

    var old = document.getElementById("photo-preview");
    if (old) old.remove();

    var img = document.createElement("img");
    img.id = "photo-preview";
    img.alt = "Original photo selected for upload";
    img.className = "cf-photo-final-preview";
    img.src = URL.createObjectURL(file);
    img.onload = function () { URL.revokeObjectURL(img.src); };
    img.onerror = function () { URL.revokeObjectURL(img.src); };
    input.insertAdjacentElement("afterend", img);

    chooserFile = null;
    if (chooserObjectUrl) URL.revokeObjectURL(chooserObjectUrl);
    chooserObjectUrl = null;
  }

  function clearSelection() {
    input.value = "";
    chooser.hidden = true;
    document.body.classList.remove("cf-photo-editor-open");
    var old = document.getElementById("photo-preview");
    if (old) old.remove();
    chooserFile = null;
    if (chooserObjectUrl) URL.revokeObjectURL(chooserObjectUrl);
    chooserObjectUrl = null;
    cleanup();
  }

  function point(e) {
    var r = canvas.getBoundingClientRect();
    return { x: (e.clientX - r.left) * 600 / r.width, y: (e.clientY - r.top) * 600 / r.height };
  }
  function hitHandle(p) {
    if (!crop) return null;

    var c = crop;
    var right = c.x + c.w;
    var bottom = c.y + c.h;
    var r = 20;

    if (Math.hypot(p.x - c.x, p.y - c.y) < r) return "tl";
    if (Math.hypot(p.x - right, p.y - c.y) < r) return "tr";
    if (Math.hypot(p.x - c.x, p.y - bottom) < r) return "bl";
    if (Math.hypot(p.x - right, p.y - bottom) < r) return "br";

    if (Math.abs(p.x - c.x) < r && p.y >= c.y + r && p.y <= bottom - r) return "l";
    if (Math.abs(p.x - right) < r && p.y >= c.y + r && p.y <= bottom - r) return "r";
    if (Math.abs(p.y - c.y) < r && p.x >= c.x + r && p.x <= right - r) return "t";
    if (Math.abs(p.y - bottom) < r && p.x >= c.x + r && p.x <= right - r) return "b";

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
    var bounds = imageBounds();
    var w = bounds.w * 0.8;
    var h = bounds.h * 0.8;

    crop = {
      x: bounds.x + (bounds.w - w) / 2,
      y: bounds.y + (bounds.h - h) / 2,
      w: w,
      h: h
    };
  }
  function render() {
    if (!image) return;
    ctx.clearRect(0, 0, 600, 600);
    ctx.fillStyle = "#080b09";
    ctx.fillRect(0, 0, 600, 600);
    var geom = drawImage(ctx, 600);
    var c = crop;
    // Dim everything outside the freeform selection.
    ctx.fillStyle = "rgba(0,0,0,.62)";
    ctx.fillRect(0, 0, 600, 600);
    ctx.save();
    ctx.beginPath();
    ctx.rect(c.x, c.y, c.w, c.h);
    ctx.clip();
    drawImage(ctx, 600);
    ctx.restore();

    // Thirds grid and rectangular crop border.
    ctx.save();
    ctx.strokeStyle = "rgba(255,255,255,.88)";
    ctx.lineWidth = 1;

    for (var i = 1; i <= 2; i++) {
      var tx = c.w * i / 3;
      var ty = c.h * i / 3;
      ctx.beginPath();
      ctx.moveTo(c.x + tx, c.y);
      ctx.lineTo(c.x + tx, c.y + c.h);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(c.x, c.y + ty);
      ctx.lineTo(c.x + c.w, c.y + ty);
      ctx.stroke();
    }

    ctx.lineWidth = 2;
    ctx.strokeRect(c.x, c.y, c.w, c.h);

    [
      [c.x, c.y],
      [c.x + c.w, c.y],
      [c.x, c.y + c.h],
      [c.x + c.w, c.y + c.h],
      [c.x + c.w / 2, c.y],
      [c.x + c.w / 2, c.y + c.h],
      [c.x, c.y + c.h / 2],
      [c.x + c.w, c.y + c.h / 2]
    ].forEach(function (p) {
      ctx.fillStyle = "#fff";
      ctx.fillRect(p[0] - 5, p[1] - 5, 10, 10);
    });

    ctx.restore();
    status.textContent = "Drag inside to move · drag any corner or edge to resize freely";
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
    image.onerror = function () {
      alert("This image format could not be opened by your browser. Try saving it as JPG or PNG and upload again.");
      input.value = "";
      cleanup();
    };
    image.src = objectUrl;
  }
  function cancel() {
    var file = originalFile;
    modal.hidden = true;
    document.body.classList.remove("cf-photo-editor-open");
    cleanup();
    if (file) showChoice(file);
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
    var sx = (crop.x - ox) / scale;
    var sy = (crop.y - oy) / scale;
    var sw = crop.w / scale;
    var sh = crop.h / scale;

    sx = Math.max(0, Math.min(d.w - sw, sx));
    sy = Math.max(0, Math.min(d.h - sh, sy));

    var full = document.createElement("canvas");
    full.width = d.w;
    full.height = d.h;
    var fctx = full.getContext("2d");
    fctx.translate(d.w / 2, d.h / 2);
    fctx.rotate(rotation * Math.PI / 2);
    fctx.drawImage(image, -image.naturalWidth / 2, -image.naturalHeight / 2);

    // Preserve the selected aspect ratio while limiting output dimensions.
    var outputScale = Math.min(1, 2048 / Math.max(sw, sh));
    var outputW = Math.max(1, Math.round(sw * outputScale));
    var outputH = Math.max(1, Math.round(sh * outputScale));

    var out = document.createElement("canvas");
    out.width = outputW;
    out.height = outputH;
    out.getContext("2d").drawImage(full, sx, sy, sw, sh, 0, 0, outputW, outputH);
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
    var mime = (file.type || "").toLowerCase();
    var extension = (file.name.match(/\\.([^.]+)$/) || ["", ""])[1].toLowerCase();
    // Some mobile browsers provide an empty MIME type; use the filename as a
    // fallback, then let the browser's image decoder verify that it is readable.
    if (!mime) {
      if (extension === "jpg" || extension === "jpeg") mime = "image/jpeg";
      else if (extension === "png") mime = "image/png";
      else if (extension === "webp") mime = "image/webp";
    }
    if (!["image/jpeg", "image/png", "image/webp"].includes(mime)) {
      input.value = "";
      alert("Please choose a JPG, PNG or WebP image. HEIC/HEIF photos need to be converted to JPG first.");
      return;
    }
    // Phone camera originals can be much larger than 5 MB. They are resized
    // and exported as a compressed JPEG preserving the selected crop dimensions by the crop editor before upload.
    if (file.size > 20 * 1024 * 1024) {
      input.value = "";
      alert("This photo is larger than 20 MB. Please choose a smaller image.");
      return;
    }
    showChoice(file);
  });
})();
