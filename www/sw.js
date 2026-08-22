"use strict";

const VERSION = "marc-v26-rc6-pwa-offline-v3-backup-native";
const APP_CACHE = `${VERSION}-app`;
const RUNTIME_CACHE = `${VERSION}-runtime`;
const APP_SHELL = ["./", "./index.html", "./manifest.webmanifest"];
const OPTIONAL_CDN_HOSTS = new Set(["cdn.jsdelivr.net", "unpkg.com"]);
const OPTIONAL_PRECACHE = [
  "https://cdn.jsdelivr.net/npm/xlsx@0.18.5/dist/xlsx.full.min.js",
  "https://cdn.jsdelivr.net/npm/body-muscles/dist/umd/body-muscles.umd.min.js"
];

self.addEventListener("install", event => {
  event.waitUntil((async()=>{
    const cache=await caches.open(APP_CACHE);
    await cache.addAll(APP_SHELL);
    const runtime=await caches.open(RUNTIME_CACHE);
    await Promise.all(OPTIONAL_PRECACHE.map(async url=>{
      try{
        const response=await fetch(url,{cache:"no-store"});
        if(response)await runtime.put(url,response.clone());
      }catch(_){ /* optional dependency must never block offline shell install */ }
    }));
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", event => {
  event.waitUntil((async()=>{
    const keep=new Set([APP_CACHE,RUNTIME_CACHE]);
    const keys=await caches.keys();
    await Promise.all(keys.filter(k=>!keep.has(k) && k.startsWith("marc-v26-")).map(k=>caches.delete(k)));
    await self.clients.claim();
  })());
});

async function navigationResponse(request){
  const cache=await caches.open(APP_CACHE);
  try{
    const fresh=await fetch(request,{cache:"no-store"});
    if(fresh && fresh.ok){
      cache.put("./index.html",fresh.clone()).catch(()=>{});
      cache.put("./",fresh.clone()).catch(()=>{});
    }
    return fresh;
  }catch(_){
    return (await cache.match(request,{ignoreSearch:true})) ||
      (await cache.match("./index.html")) ||
      (await cache.match("./")) ||
      new Response("M/ARC is unavailable offline until it has been opened online once.",{status:503,headers:{"Content-Type":"text/plain;charset=utf-8"}});
  }
}

async function sameOriginAsset(request){
  const cached=await caches.match(request,{ignoreSearch:true});
  if(cached)return cached;
  try{
    const fresh=await fetch(request);
    if(fresh && fresh.ok){
      const cache=await caches.open(RUNTIME_CACHE);
      cache.put(request,fresh.clone()).catch(()=>{});
    }
    return fresh;
  }catch(_){
    return new Response("Offline",{status:503,statusText:"Offline"});
  }
}

async function optionalCdn(request){
  const cache=await caches.open(RUNTIME_CACHE);
  const cached=await cache.match(request);
  if(cached){
    fetch(request).then(r=>{if(r)cache.put(request,r.clone()).catch(()=>{})}).catch(()=>{});
    return cached;
  }
  const fresh=await fetch(request);
  if(fresh)cache.put(request,fresh.clone()).catch(()=>{});
  return fresh;
}

self.addEventListener("fetch", event => {
  const request=event.request;
  if(request.method!=="GET")return;
  const url=new URL(request.url);
  if(request.mode==="navigate"){
    event.respondWith(navigationResponse(request));
    return;
  }
  if(url.origin===self.location.origin){
    event.respondWith(sameOriginAsset(request));
    return;
  }
  if(OPTIONAL_CDN_HOSTS.has(url.hostname)){
    event.respondWith(optionalCdn(request));
  }
});
