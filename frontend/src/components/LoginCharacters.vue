<template>
<div ref="scene" class="characters-scene" aria-hidden="true">
          <div
            id="purple-character"
            class="absolute bottom-0 character-transition"
            style="left: 70px; width: 180px; height: 400px; background-color: #6C3FF5; border-radius: 10px 10px 0 0; z-index: 1;"
          >
            <div id="purple-eyes" class="absolute flex gap-8 face-transition" style="left: 45px; top: 40px;">
              <div id="purple-eye-1" class="eye-ball" style="width: 18px; height: 18px; background-color: white;">
                <div id="purple-pupil-1" class="rounded-full pupil-transition" style="width: 7px; height: 7px; background-color: #2D2D2D;"></div>
              </div>
              <div id="purple-eye-2" class="eye-ball" style="width: 18px; height: 18px; background-color: white;">
                <div id="purple-pupil-2" class="rounded-full pupil-transition" style="width: 7px; height: 7px; background-color: #2D2D2D;"></div>
              </div>
            </div>
          </div>

          <div
            id="black-character"
            class="absolute bottom-0 character-transition"
            style="left: 240px; width: 120px; height: 310px; background-color: #2D2D2D; border-radius: 8px 8px 0 0; z-index: 2;"
          >
            <div id="black-eyes" class="absolute flex gap-6 face-transition" style="left: 26px; top: 32px;">
              <div id="black-eye-1" class="eye-ball" style="width: 16px; height: 16px; background-color: white;">
                <div id="black-pupil-1" class="rounded-full pupil-transition" style="width: 6px; height: 6px; background-color: #2D2D2D;"></div>
              </div>
              <div id="black-eye-2" class="eye-ball" style="width: 16px; height: 16px; background-color: white;">
                <div id="black-pupil-2" class="rounded-full pupil-transition" style="width: 6px; height: 6px; background-color: #2D2D2D;"></div>
              </div>
            </div>
          </div>

          <div
            id="orange-character"
            class="absolute bottom-0 character-transition"
            style="left: 0; width: 240px; height: 200px; background-color: #FF9B6B; border-radius: 120px 120px 0 0; z-index: 3;"
          >
            <div id="orange-eyes" class="absolute flex gap-8 fast-face-transition" style="left: 82px; top: 90px;">
              <div id="orange-pupil-1" class="rounded-full pupil-transition" style="width: 12px; height: 12px; background-color: #2D2D2D;"></div>
              <div id="orange-pupil-2" class="rounded-full pupil-transition" style="width: 12px; height: 12px; background-color: #2D2D2D;"></div>
            </div>
          </div>

          <div
            id="yellow-character"
            class="absolute bottom-0 character-transition"
            style="left: 310px; width: 140px; height: 230px; background-color: #E8D754; border-radius: 70px 70px 0 0; z-index: 4;"
          >
            <div id="yellow-eyes" class="absolute flex gap-6 fast-face-transition" style="left: 52px; top: 40px;">
              <div id="yellow-pupil-1" class="rounded-full pupil-transition" style="width: 12px; height: 12px; background-color: #2D2D2D;"></div>
              <div id="yellow-pupil-2" class="rounded-full pupil-transition" style="width: 12px; height: 12px; background-color: #2D2D2D;"></div>
            </div>
            <div id="yellow-mouth" class="absolute fast-face-transition" style="left: 40px; top: 88px;"></div>
          </div>
        </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'

const props = defineProps({ isTyping: Boolean, showPassword: Boolean, passwordLength: { type: Number, default: 0 } })
const scene = ref()
const state = reactive({ mouseX: 0, mouseY: 0, isPurpleBlinking: false, isBlackBlinking: false, isLookingAtEachOther: false, isPurplePeeking: false })
const refs = {}
const timers = new Set()
let frame = 0, lookTimer, peekTimer, peekResetTimer, reducedMotion = false
function later(callback, delay) {
  const timer = setTimeout(() => { timers.delete(timer); callback() }, delay)
  timers.add(timer)
  return timer
}
function cancel(timer) { clearTimeout(timer); timers.delete(timer) }
    function clamp(value, min, max) {
      return Math.max(min, Math.min(max, value));
    }

    function calculatePupilPosition(element, maxDistance, forceLookX, forceLookY) {
      if (forceLookX !== undefined && forceLookY !== undefined) {
        return { x: forceLookX, y: forceLookY };
      }

      const rect = element.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2;
      const centerY = rect.top + rect.height / 2;
      const deltaX = state.mouseX - centerX;
      const deltaY = state.mouseY - centerY;
      const distance = Math.min(Math.sqrt(deltaX ** 2 + deltaY ** 2), maxDistance);
      const angle = Math.atan2(deltaY, deltaX);

      return {
        x: Math.cos(angle) * distance,
        y: Math.sin(angle) * distance,
      };
    }

    function calculatePosition(element) {
      const rect = element.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2;
      const centerY = rect.top + rect.height / 3;
      const deltaX = state.mouseX - centerX;
      const deltaY = state.mouseY - centerY;

      return {
        faceX: clamp(deltaX / 20, -15, 15),
        faceY: clamp(deltaY / 30, -10, 10),
        bodySkew: clamp(-deltaX / 120, -6, 6),
      };
    }

    function setElementTranslate(element, position) {
      element.style.transform = `translate(${position.x}px, ${position.y}px)`;
    }

    function updateEyeBall(eyeElement, pupilElement, options) {
      const { size, maxDistance, isBlinking, forceLookX, forceLookY } = options;
      eyeElement.style.width = `${size}px`;
      eyeElement.style.height = isBlinking ? "2px" : `${size}px`;
      pupilElement.style.display = isBlinking ? "none" : "block";

      if (!isBlinking) {
        const position = calculatePupilPosition(eyeElement, maxDistance, forceLookX, forceLookY);
        setElementTranslate(pupilElement, position);
      }
    }

    function updatePupil(element, options) {
      const { maxDistance, forceLookX, forceLookY } = options;
      const position = calculatePupilPosition(element, maxDistance, forceLookX, forceLookY);
      setElementTranslate(element, position);
    }

    function renderCharacters() {
      const purplePos = calculatePosition(refs.purpleCharacter);
      const blackPos = calculatePosition(refs.blackCharacter);
      const yellowPos = calculatePosition(refs.yellowCharacter);
      const orangePos = calculatePosition(refs.orangeCharacter);
      const isHidingPassword = props.passwordLength > 0 && !props.showPassword;

      refs.purpleCharacter.style.height = (props.isTyping || isHidingPassword) ? "440px" : "400px";
      refs.purpleCharacter.style.transform = props.passwordLength > 0 && props.showPassword
        ? "skewX(0deg)"
        : (props.isTyping || isHidingPassword)
          ? `skewX(${(purplePos.bodySkew || 0) - 12}deg) translateX(40px)`
          : `skewX(${purplePos.bodySkew || 0}deg)`;

      refs.blackCharacter.style.transform = props.passwordLength > 0 && props.showPassword
        ? "skewX(0deg)"
        : state.isLookingAtEachOther
          ? `skewX(${(blackPos.bodySkew || 0) * 1.5 + 10}deg) translateX(20px)`
          : (props.isTyping || isHidingPassword)
            ? `skewX(${(blackPos.bodySkew || 0) * 1.5}deg)`
            : `skewX(${blackPos.bodySkew || 0}deg)`;

      refs.orangeCharacter.style.transform = props.passwordLength > 0 && props.showPassword
        ? "skewX(0deg)"
        : `skewX(${orangePos.bodySkew || 0}deg)`;

      refs.yellowCharacter.style.transform = props.passwordLength > 0 && props.showPassword
        ? "skewX(0deg)"
        : `skewX(${yellowPos.bodySkew || 0}deg)`;

      refs.purpleEyes.style.left = (props.passwordLength > 0 && props.showPassword) ? "20px" : state.isLookingAtEachOther ? "55px" : `${45 + purplePos.faceX}px`;
      refs.purpleEyes.style.top = (props.passwordLength > 0 && props.showPassword) ? "35px" : state.isLookingAtEachOther ? "65px" : `${40 + purplePos.faceY}px`;
      refs.blackEyes.style.left = (props.passwordLength > 0 && props.showPassword) ? "10px" : state.isLookingAtEachOther ? "32px" : `${26 + blackPos.faceX}px`;
      refs.blackEyes.style.top = (props.passwordLength > 0 && props.showPassword) ? "28px" : state.isLookingAtEachOther ? "12px" : `${32 + blackPos.faceY}px`;
      refs.orangeEyes.style.left = (props.passwordLength > 0 && props.showPassword) ? "50px" : `${82 + (orangePos.faceX || 0)}px`;
      refs.orangeEyes.style.top = (props.passwordLength > 0 && props.showPassword) ? "85px" : `${90 + (orangePos.faceY || 0)}px`;
      refs.yellowEyes.style.left = (props.passwordLength > 0 && props.showPassword) ? "20px" : `${52 + (yellowPos.faceX || 0)}px`;
      refs.yellowEyes.style.top = (props.passwordLength > 0 && props.showPassword) ? "35px" : `${40 + (yellowPos.faceY || 0)}px`;
      refs.yellowMouth.style.left = (props.passwordLength > 0 && props.showPassword) ? "10px" : `${40 + (yellowPos.faceX || 0)}px`;
      refs.yellowMouth.style.top = (props.passwordLength > 0 && props.showPassword) ? "88px" : `${88 + (yellowPos.faceY || 0)}px`;

      const purpleForceX = (props.passwordLength > 0 && props.showPassword) ? (state.isPurplePeeking ? 4 : -4) : state.isLookingAtEachOther ? 3 : undefined;
      const purpleForceY = (props.passwordLength > 0 && props.showPassword) ? (state.isPurplePeeking ? 5 : -4) : state.isLookingAtEachOther ? 4 : undefined;
      const blackForceX = (props.passwordLength > 0 && props.showPassword) ? -4 : state.isLookingAtEachOther ? 0 : undefined;
      const blackForceY = (props.passwordLength > 0 && props.showPassword) ? -4 : state.isLookingAtEachOther ? -4 : undefined;
      const frontForceX = (props.passwordLength > 0 && props.showPassword) ? -5 : undefined;
      const frontForceY = (props.passwordLength > 0 && props.showPassword) ? -4 : undefined;

      updateEyeBall(refs.purpleEye1, refs.purplePupil1, {
        size: 18,
        maxDistance: 5,
        isBlinking: state.isPurpleBlinking,
        forceLookX: purpleForceX,
        forceLookY: purpleForceY,
      });
      updateEyeBall(refs.purpleEye2, refs.purplePupil2, {
        size: 18,
        maxDistance: 5,
        isBlinking: state.isPurpleBlinking,
        forceLookX: purpleForceX,
        forceLookY: purpleForceY,
      });
      updateEyeBall(refs.blackEye1, refs.blackPupil1, {
        size: 16,
        maxDistance: 4,
        isBlinking: state.isBlackBlinking,
        forceLookX: blackForceX,
        forceLookY: blackForceY,
      });
      updateEyeBall(refs.blackEye2, refs.blackPupil2, {
        size: 16,
        maxDistance: 4,
        isBlinking: state.isBlackBlinking,
        forceLookX: blackForceX,
        forceLookY: blackForceY,
      });
      updatePupil(refs.orangePupil1, { maxDistance: 5, forceLookX: frontForceX, forceLookY: frontForceY });
      updatePupil(refs.orangePupil2, { maxDistance: 5, forceLookX: frontForceX, forceLookY: frontForceY });
      updatePupil(refs.yellowPupil1, { maxDistance: 5, forceLookX: frontForceX, forceLookY: frontForceY });
      updatePupil(refs.yellowPupil2, { maxDistance: 5, forceLookX: frontForceX, forceLookY: frontForceY });
    }


function scheduleBlink(key) {
  later(() => {
    state[key] = true
    renderCharacters()
    later(() => { state[key] = false; renderCharacters(); scheduleBlink(key) }, 150)
  }, Math.random() * 4000 + 3000)
}
function triggerLookingAtEachOther() {
  cancel(lookTimer)
  state.isLookingAtEachOther = true
  renderCharacters()
  lookTimer = later(() => { state.isLookingAtEachOther = false; renderCharacters() }, 800)
}
function schedulePeek() {
  cancel(peekTimer)
  cancel(peekResetTimer)
  state.isPurplePeeking = false
  if (!reducedMotion && props.passwordLength > 0 && props.showPassword) {
    peekTimer = later(() => {
      state.isPurplePeeking = true
      renderCharacters()
      peekResetTimer = later(() => { state.isPurplePeeking = false; renderCharacters(); schedulePeek() }, 800)
    }, Math.random() * 3000 + 2000)
  }
}
function moveEyes(event) {
  if (reducedMotion) return
  state.mouseX = event.clientX
  state.mouseY = event.clientY
  if (!frame) frame = requestAnimationFrame(() => { frame = 0; renderCharacters() })
}
watch(() => props.isTyping, typing => {
  if (typing && !reducedMotion) triggerLookingAtEachOther()
  else renderCharacters()
})
watch(() => [props.passwordLength, props.showPassword], () => { schedulePeek(); renderCharacters() })
onMounted(() => {
  refs.purpleCharacter = scene.value.querySelector("#purple-character")
  refs.blackCharacter = scene.value.querySelector("#black-character")
  refs.orangeCharacter = scene.value.querySelector("#orange-character")
  refs.yellowCharacter = scene.value.querySelector("#yellow-character")
  refs.purpleEyes = scene.value.querySelector("#purple-eyes")
  refs.blackEyes = scene.value.querySelector("#black-eyes")
  refs.orangeEyes = scene.value.querySelector("#orange-eyes")
  refs.yellowEyes = scene.value.querySelector("#yellow-eyes")
  refs.yellowMouth = scene.value.querySelector("#yellow-mouth")
  refs.purpleEye1 = scene.value.querySelector("#purple-eye-1")
  refs.purpleEye2 = scene.value.querySelector("#purple-eye-2")
  refs.blackEye1 = scene.value.querySelector("#black-eye-1")
  refs.blackEye2 = scene.value.querySelector("#black-eye-2")
  refs.purplePupil1 = scene.value.querySelector("#purple-pupil-1")
  refs.purplePupil2 = scene.value.querySelector("#purple-pupil-2")
  refs.blackPupil1 = scene.value.querySelector("#black-pupil-1")
  refs.blackPupil2 = scene.value.querySelector("#black-pupil-2")
  refs.orangePupil1 = scene.value.querySelector("#orange-pupil-1")
  refs.orangePupil2 = scene.value.querySelector("#orange-pupil-2")
  refs.yellowPupil1 = scene.value.querySelector("#yellow-pupil-1")
  refs.yellowPupil2 = scene.value.querySelector("#yellow-pupil-2")
  reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  state.mouseX = window.innerWidth / 2
  state.mouseY = window.innerHeight / 2
  window.addEventListener('mousemove', moveEyes, { passive: true })
  window.addEventListener('resize', renderCharacters)
  if (!reducedMotion) { scheduleBlink('isPurpleBlinking'); scheduleBlink('isBlackBlinking') }
  renderCharacters()
})
onUnmounted(() => {
  window.removeEventListener('mousemove', moveEyes)
  window.removeEventListener('resize', renderCharacters)
  cancelAnimationFrame(frame)
  timers.forEach(clearTimeout)
  timers.clear()
})

</script>

<style scoped>

.characters-scene { position:relative; width:550px; height:400px; flex-shrink:0; }
.absolute { position:absolute; }
.bottom-0 { bottom:0; }
.flex { display:flex; }
.gap-8 { gap:32px; }
.gap-6 { gap:24px; }
.rounded-full { border-radius:9999px; }
.eye-ball { display:flex; align-items:center; justify-content:center; border-radius:9999px; overflow:hidden; transition:height 150ms; }
.character-transition { transition:transform 700ms ease-in-out, height 700ms ease-in-out; transform-origin:bottom center; }
.face-transition { transition:left 700ms ease-in-out, top 700ms ease-in-out; }
.fast-face-transition { transition:left 200ms ease-out, top 200ms ease-out; }
.pupil-transition { transition:transform 100ms ease-out; }
#yellow-mouth { width:80px; height:4px; background:#2d2d2d; border-radius:9999px; }
@media (max-width:1200px) { .characters-scene { transform:scale(.85); } }
@media (prefers-reduced-motion:reduce) { .character-transition, .face-transition, .fast-face-transition, .pupil-transition, .eye-ball { transition:none; } }
</style>
