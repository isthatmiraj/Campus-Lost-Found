(function () {
  "use strict";
  var input = document.querySelector('input[type="file"][name="photo"]');
  if (!input) return;

  var MAX_FILE_SIZE = 5 * 1024 * 1024;
  var sourceImage = null;
  var sourceFile = null;
  var rotation = 0;
  var zoom = 1;
  var panX = 0;
  var panY = 0;
  var dragging = false;
  var lastX = 0;
  var lastY = 0;
  var objectUrl = null;
  var modal, canvas, ctx, zoomControl, preview, status;

  function makeEditor() {
    modal = document.createElement("div");
    modal.className = "cf-photo-editor";
    modal.hidden = true;
    modal.innerHTML =
      '<div class="cf-photo-editor__dialog" role="dialog" aria-modal="true" aria-labelledby="cf-photo-editor-title">' +
        '<div class="cf-photo-editor__header"><div><p class="eyebrow">PHOTO EDITOR</p><h2 id="cf-photo-editor-title">Adjust your photo</h2></div>' +
        '<button type="button" class="cf-photo-editor__close" data-action="cancel" aria-label="Close editor">×</button></div>' +
        '<p class="cf-photo-editor__hint">Drag the photo to reposition it. Use zoom and rotate to get the crop right.</p>' +
        '<div class="cf-photo-editor__stage"><canvas width="720" height="540" aria-label="Photo crop preview"></canvas></div>' +
        '<div class="cf-photo-editor__tools"><button type="button" class="btn btn-light" data-action="left">↶ Rotate left</button>' +
        '<button type="button" class="btn btn-light" data-action="right">↷ Rotate right</button></div>' +
        '<label class="cf-photo-editor__zoom">Zoom <input type="range" min="1" max="3" step="0.01" value="1"></label>' +
        '<p class="cf-photo-editor__status" aria-live="polite"></p>' +
        '<div class="cf-photo-editor__footer"><button type="button" class="btn btn-light" data-action="cancel">Cancel</button>' +
        '<button type="button" class="btn" data-action="apply">Use this photo</button></div>' +
      '</div>';
    document.body.appendChild(modal);
    canvas = modal.querySelector("canvas");
    ctx = canvas.getContext("2d");
    zoomControl = modal.querySelector('input[type="range"]');
    preview = modal.querySelector(".cf-photo-editor__stage");
    status = modal.querySelector(".cf-photo-editor__status");

    modal.addEventListener("click", function (event) {
      if (event.target === modal) cancel();
      var button = event.target.closest("[data-action]");
      if (!button) return;
      var action = button.getAttribute("data-action");
      if (action === "cancel") cancel();
      if (action === "left") { rotation = (rotation + 3) % 4; resetPan(); render(); }
      if (action === "right") { rotation = (rotation + 1) % 4; resetPan(); render(); }
      if (action === "apply") apply();
    });
    zoomControl.addEventListener("input", function () {
      zoom = Number(zoomControl.value);
      render();
    });
    canvas.addEventListener("pointerdown", function (event) {
      dragging = true;
      lastX = event.clientX;
      lastY = event.clientY;
      canvas.setPointerCapture(event.pointerId);
      canvas.classList.add("is-dragging");
    });
    canvas.addEventListener("pointermove", function (event) {
      if (!dragging) return;
      panX += (event.clientX - lastX) * (canvas.width / canvas.getBoundingClientRect().width);
      panY += (event.clientY - lastY) * (canvas.height / canvas.getBoundingClientRect().height);
      lastX = event.clientX;
      lastY = event.clientY;
      render();
    });
    ["pointerup", "pointercancel", "lostpointercapture"].forEach(function (name) {
      canvas.addEventListener(name, function () {
        dragging = false;
        canvas.classList.remove("is-dragging");
      });
    });
    document.addEventListener("keydown", function (event) {
      if (!modal.hidden && event.key === "Escape") cancel();
    });
  }

  function resetPan() { panX = 0; panY = 0; }
  function getGeometry() {
    var w = sourceImage.naturalWidth;
    var h = sourceImage.naturalHeight;
    var swapped = rotation % 2 === 1;
    var rw = swapped ? h : w;
    var rh = swapped ? w : h;
    var base = Math.max(canvas.width / rw, canvas.height / rh);
    return { scale: base * zoom, width: rw, height: rh };
  }
  function draw(target, scaleFactor) {
    var width = target.width;
    var height = target.height;
    var factor = scaleFactor || 1;
    target.width = width;
    target.height = height;
    var c = target.getContext("2d");
    c.fillStyle = "#101512";
    c.fillRect(0, 0, width, height);
    var g = getGeometry();
    c.save();
    c.beginPath();
    c.rect(0, 0, width, height);
    c.clip();
    c.translate(width / 2 + panX * factor, height / 2 + panY * factor);
    c.rotate(rotation * Math.PI / 2);
    c.scale(g.scale * factor, g.scale * factor);
    c.drawImage(sourceImage, -sourceImage.naturalWidth / 2, -sourceImage.naturalHeight / 2);
    c.restore();
  }
  function render() {
    if (!sourceImage) return;
    draw(canvas, 1);
    status.textContent = "4:3 crop · " + Math.round(zoom * 100) + "% zoom";
  }
  function openEditor(file) {
    sourceFile = file;
    rotation = 0;
    zoom = 1;
    zoomControl.value = "1";
    resetPan();
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = URL.createObjectURL(file);
    sourceImage = new Image();
    sourceImage.onload = function () {
      modal.hidden = false;
      document.body.classList.add("cf-photo-editor-open");
      render();
      modal.querySelector('[data-action="apply"]').focus();
    };
    sourceImage.onerror = function () {
      alert("This image could not be opened. Please choose another JPG or PNG.");
      input.value = "";
    };
    sourceImage.src = objectUrl;
  }
  function cancel() {
    modal.hidden = true;
    document.body.classList.remove("cf-photo-editor-open");
    input.value = "";
    sourceImage = null;
    sourceFile = null;
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = null;
  }
  function apply() {
    if (!sourceImage || !sourceFile) return;
    var output = document.createElement("canvas");
    output.width = 1280;
    output.height = 960;
    draw(output, output.width / canvas.width);
    output.toBlob(function (blob) {
      if (!blob) {
        alert("Could not process this photo. Please try another image.");
        return;
      }
      var edited = new File([blob], (sourceFile.name.replace(/\.[^.]+$/, "") || "campusfind-photo") + "-edited.jpg", {
        type: "image/jpeg",
        lastModified: Date.now()
      });
      var transfer = new DataTransfer();
      transfer.items.add(edited);
      input.files = transfer.files;
      modal.hidden = true;
      document.body.classList.remove("cf-photo-editor-open");
      var old = document.getElementById("photo-preview");
      if (old) old.remove();
      var img = document.createElement("img");
      img.id = "photo-preview";
      img.alt = "Edited item photo preview";
      img.src = URL.createObjectURL(edited);
      img.className = "cf-photo-final-preview";
      input.insertAdjacentElement("afterend", img);
      sourceImage = null;
      sourceFile = null;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      objectUrl = null;
    }, "image/jpeg", 0.9);
  }

  makeEditor();
  input.addEventListener("change", function () {
    var file = input.files && input.files[0];
    var old = document.getElementById("photo-preview");
    if (old) old.remove();
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type)) {
      input.value = "";
      alert("Choose a JPG or PNG image.");
      return;
    }
    if (file.size > MAX_FILE_SIZE) {
      input.value = "";
      alert("Image must be 5 MB or smaller.");
      return;
    }
    openEditor(file);
  });
})();
