// Renders animated frame sequences of PhantomFloor scenes with their real shaders.
const fs = require('fs'), path = require('path');
const { chromium } = require(process.env.PW);
const dir = process.argv[2], outDir = process.argv[3], only = process.argv[4];
const W = 480, H = 270;
function load(p) { return fs.readFileSync(path.join(dir, p), 'utf8').split('\n').map(l => l.trim().startsWith('#include') ? load(l.split('"')[1]) : l).join('\n'); }
const src = {}; for (const f of fs.readdirSync(dir)) src[f] = load(f);

const SETUP = `
window.W=${W}; window.H=${H}; window.A=W/H;
const gl = document.getElementById('c').getContext('webgl2', { preserveDrawingBuffer: true, antialias: false });
window.gl = gl; gl.getExtension('EXT_color_buffer_float'); gl.getExtension('OES_texture_float_linear');
window.progs = {};
function sh(t,s,n){const x=gl.createShader(t);gl.shaderSource(x,s);gl.compileShader(x);if(!gl.getShaderParameter(x,gl.COMPILE_STATUS))throw new Error(n+gl.getShaderInfoLog(x));return x;}
window.mk=function(v,f){const p=gl.createProgram();gl.attachShader(p,sh(gl.VERTEX_SHADER,SRC[v],v));gl.attachShader(p,sh(gl.FRAGMENT_SHADER,SRC[f],f));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(f+gl.getProgramInfoLog(p));progs[f]=p;};
for (const f of ['fog','smoke','swarm_floor','ripple_update','fluid','water_shapes','water','fluid_advect','fluid_splat','fluid_curl','fluid_vorticity','fluid_divergence','fluid_pressure','fluid_gradient','fluid_scale','snow_cover','snow_ground','garden_growth','garden','solid','copy','night_lawn','pool','earth_ground']) mk('fullscreen.vert', f+'.frag');
mk('spider.vert','spider.frag'); for (const f of ['spark','snowflake','leaf']) mk('sprite.vert', f+'.frag');
window.vao=gl.createVertexArray(); window.P=null;
window.u=n=>gl.getUniformLocation(P,n); window.f1=(n,v)=>gl.uniform1f(u(n),v); window.f2=(n,a,b)=>gl.uniform2f(u(n),a,b); window.i1=(n,v)=>gl.uniform1i(u(n),v);
window.use=f=>{P=progs[f];gl.useProgram(P);};
window.tex=(n,unit,t)=>{gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,t);i1(n,unit);};
window.target=function(w,h,flt){w=Math.max(1,w|0);h=Math.max(1,h|0);const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);
 if(flt)gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA16F,w,h,0,gl.RGBA,gl.HALF_FLOAT,null);else gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,null);
 for(const [k,v] of [[gl.TEXTURE_MIN_FILTER,gl.LINEAR],[gl.TEXTURE_MAG_FILTER,gl.LINEAR],[gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE],[gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE]])gl.texParameteri(gl.TEXTURE_2D,k,v);
 const fb=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,t,0);return{t,fb,w,h};};
window.pp=(w,h,flt)=>{const o={a:target(w,h,flt),b:target(w,h,flt)};o.swap=()=>{const t=o.a;o.a=o.b;o.b=t;};return o;};
window.bind=tg=>{gl.bindFramebuffer(gl.FRAMEBUFFER,tg?tg.fb:null);gl.viewport(0,0,tg?tg.w:W,tg?tg.h:H);};
window.clear=(r=0,g=0,b=0,a=1)=>{gl.clearColor(r,g,b,a);gl.clear(gl.COLOR_BUFFER_BIT);};
window.fsq=()=>{gl.bindVertexArray(vao);gl.drawArrays(gl.TRIANGLES,0,3);};
window.common=function(t,people,beat){gl.uniform2f(u('uResolution'),W,H);f1('uTime',t);const arr=new Float32Array(32).fill(-10);people.forEach((q,i)=>arr.set(q,i*4));
 gl.uniform4fv(u('uPeople'),arr);i1('uPeopleCount',people.length);f1('uLevel',0.15);f1('uBass',0.1);f1('uMid',0);f1('uTreble',0.1);f1('uBeat',beat||0);};
window.snap=()=>document.getElementById('c').toDataURL('image/png');
let seed=7; window.rnd=()=>(seed=(seed*16807)%2147483647)/2147483647;
const sprVao=gl.createVertexArray(),sprBuf=gl.createBuffer();gl.bindVertexArray(sprVao);gl.bindBuffer(gl.ARRAY_BUFFER,sprBuf);
for(let a=0;a<3;a++){gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,4,gl.FLOAT,false,48,a*16);gl.vertexAttribDivisor(a,1);}
window.sprites=function(list,prog,t,setup){use(prog);f1('uAspect',A);f2('uOffset',0,0);f1('uTime',t||0);setup&&setup();const d=new Float32Array(Math.max(1,list.length)*12);list.forEach((s,i)=>d.set(s,i*12));
 gl.bindVertexArray(sprVao);gl.bindBuffer(gl.ARRAY_BUFFER,sprBuf);gl.bufferData(gl.ARRAY_BUFFER,d,gl.DYNAMIC_DRAW);gl.drawArraysInstanced(gl.TRIANGLE_STRIP,0,4,list.length);};
// walker: uv position and velocity over t in [0,1]
window.walk=t=>{const x=0.08+0.84*t,y=0.5+0.22*Math.sin(t*5.0);return [x,y,0.84/4,0.22*5*Math.cos(t*5)/4];};
window.ripple=function(rp,drops,enc){const rw=rp.a.w,rs=rp.a.h;bind(rp.b);use('ripple_update.frag');tex('uState',0,rp.a.t);f2('uTexel',1/rw,1/rs);f1('uDamping',0.985);f1('uAspect',A);f1('uEncoded',enc);
 const d=new Float32Array(96);drops.forEach((q,i)=>d.set(q,i*4));i1('uDropCount',drops.length);gl.uniform4fv(u('uDrops'),d);fsq();rp.swap();};
`;

const SCENES = {
fog: `const F=[];for(let f=0;f<60;f++){const t=f/59;const w=walk(t);bind(null);clear();use('fog.frag');common(f/12,[w,[0.72,0.32,0,0]]);
 f1('uFlowTime',6+f*0.06);f1('uDensity',0.62);f1('uScale',1.6);f1('uHue',0.55);f1('uClearRadius',0.15);f1('uWisps',0.6);fsq();F.push(snap());}return F;`,
smoke: `const sh=96,sw=Math.round(sh*A),tx=1/sw,ty=1/sh;const vel=pp(sw,sh,1),dye=pp(sw,sh,1),pr=pp(sw,sh,1),div=target(sw,sh,1),curl=target(sw,sh,1);
 for(const t of [vel.a,vel.b,dye.a,dye.b,pr.a,pr.b,div,curl]){bind(t);clear(0,0,0,0);}const dt=1/30;const F=[];
 function step(step,walker){const time=step*dt;const vs=[],ds=[];for(let i=0;i<3;i++){const along=(i+0.5)/3+0.08*Math.sin(time*0.3+i*2.1);const s=((along%1)+1)%1*4;const e=Math.min(3,s|0);
  const pt=[[s,0.01,0,1],[0.99,s-1,-1,0],[1-(s-2),0.99,0,-1],[0.01,1-(s-3),1,0]][e];const sw2=0.6*Math.sin(time*0.4+i*1.3);const dx=pt[2]*Math.cos(sw2)-pt[3]*Math.sin(sw2),dy=pt[2]*Math.sin(sw2)+pt[3]*Math.cos(sw2);
  ds.push([pt[0],pt[1],0.08,0],[dt*7,0,0,0]);vs.push([pt[0],pt[1],0.07,0],[dx*600*dt,dy*600*dt,0,0]);}
  if(walker){vs.push([walker[0],walker[1],0.05,0],[walker[2]*sw*0.6,walker[3]*sh*0.6,0,(20+60*0.5)*dt*6]);ds.push([walker[0],walker[1],0.035,0],[-dt*1.5,0,0,0]);}
  const splat=(field,list,cm)=>{bind(field.b);use('fluid_splat.frag');f1('uAspect',A);f1('uClampMin',cm);const Pp=new Float32Array(64),V=new Float32Array(64);for(let k=0;k<list.length/2;k++){Pp.set(list[2*k],k*4);V.set(list[2*k+1],k*4);}
   i1('uSplatCount',list.length/2);gl.uniform4fv(u('uSplatPos'),Pp);gl.uniform4fv(u('uSplatVal'),V);tex('uTarget',0,field.a.t);fsq();field.swap();};
  splat(vel,vs,-1e4);splat(dye,ds,0);
  bind(curl);use('fluid_curl.frag');f2('uTexel',tx,ty);tex('uVelocity',0,vel.a.t);fsq();
  bind(vel.b);use('fluid_vorticity.frag');f2('uTexel',tx,ty);f1('uDt',dt);f1('uCurlStrength',25);f1('uBreeze',30);f1('uTime',time);tex('uVelocity',0,vel.a.t);tex('uCurl',1,curl.t);fsq();vel.swap();
  bind(div);use('fluid_divergence.frag');f2('uTexel',tx,ty);tex('uVelocity',0,vel.a.t);fsq();
  bind(pr.b);use('fluid_scale.frag');f1('uScale',0.8);tex('uSource',0,pr.a.t);fsq();pr.swap();
  use('fluid_pressure.frag');f2('uTexel',tx,ty);tex('uDivergence',1,div.t);for(let k=0;k<20;k++){bind(pr.b);tex('uPressure',0,pr.a.t);fsq();pr.swap();}
  bind(vel.b);use('fluid_gradient.frag');f2('uTexel',tx,ty);tex('uPressure',0,pr.a.t);tex('uVelocity',1,vel.a.t);fsq();vel.swap();
  use('fluid_advect.frag');f2('uTexel',tx,ty);f1('uDt',dt);bind(vel.b);f1('uDissipation',0.85);tex('uVelocity',0,vel.a.t);tex('uSource',1,vel.a.t);fsq();vel.swap();
  bind(dye.b);f1('uDissipation',0.83);tex('uVelocity',0,vel.a.t);tex('uSource',1,dye.a.t);fsq();dye.swap();}
 for(let s=0;s<240;s++)step(s,null);
 for(let f=0;f<60;f++){const w=walk(f/59);step(240+2*f,w);step(241+2*f,w);bind(null);clear();use('smoke.frag');common((240+2*f)*dt,[w]);
  f1('uSimulated',1);f2('uSimTexel',tx,ty);gl.uniform3f(u('uTint'),0.82,0.85,0.9);f1('uBrightness',0.85);tex('uDensity',0,dye.a.t);tex('uVelocity',1,vel.a.t);fsq();F.push(snap());}return F;`,
swarm: `const N=170,x=[],y=[],vx=[],vy=[],hd=[],gait=[],panic=[],sz=[];for(let i=0;i<N;i++){x.push(0.15*A+rnd()*0.7*A);y.push(0.1+rnd()*0.8);vx.push(0);vy.push(0);hd.push(rnd()*6.28);gait.push(rnd()*6);panic.push(0);sz.push(0.75+rnd()*0.5);}
 const dt=1/15,F=[];const ivao=gl.createVertexArray(),buf=gl.createBuffer();gl.bindVertexArray(ivao);gl.bindBuffer(gl.ARRAY_BUFFER,buf);
 gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,4,gl.FLOAT,false,24,0);gl.vertexAttribDivisor(0,1);gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,2,gl.FLOAT,false,24,16);gl.vertexAttribDivisor(1,1);
 for(let f=0;f<64;f++){const t=f/63;const w=walk(Math.max(0,(f-8)/55));const people=f>=8?[w]:[];
  for(let i=0;i<N;i++){let fx=Math.cos(hd[i]+Math.sin(f*0.2+i))*0.03,fy=Math.sin(hd[i]+Math.sin(f*0.2+i))*0.03;
   for(const p of people){const dx=x[i]-p[0]*A,dy=y[i]-p[1];const d=Math.hypot(dx,dy);if(d<0.24){const k=1-d/0.24;fx+=dx/Math.max(d,0.01)*k*k*1.6;fy+=dy/Math.max(d,0.01)*k*k*1.6;panic[i]=Math.max(panic[i],k);}}
   if(x[i]<0.05)fx+=0.5;if(x[i]>A-0.05)fx-=0.5;if(y[i]<0.05)fy+=0.5;if(y[i]>0.95)fy-=0.5;
   vx[i]+=(fx-vx[i]*1.5)*dt*3;vy[i]+=(fy-vy[i]*1.5)*dt*3;const lim=0.08*(1+panic[i]*3);const sp=Math.hypot(vx[i],vy[i]);if(sp>lim){vx[i]*=lim/sp;vy[i]*=lim/sp;}
   x[i]+=vx[i]*dt;y[i]+=vy[i]*dt;if(sp>0.003)hd[i]=Math.atan2(vy[i],vx[i]);gait[i]+=sp/(0.028*sz[i])*dt*4;panic[i]*=0.93;}
  bind(null);clear();use('swarm_floor.frag');common(f/15,people);f1('uLight',0.7);f2('uFlash',(0.75-0.25*Math.sin(f*0.08))*A,0.55+0.2*Math.cos(f*0.11));f1('uFlashRadius',0.18);f1('uFlashPower',0.9);fsq();
  const d=new Float32Array(N*6);for(let i=0;i<N;i++)d.set([x[i],y[i],hd[i],0.028*sz[i],gait[i],panic[i]],i*6);
  gl.bindVertexArray(ivao);gl.bindBuffer(gl.ARRAY_BUFFER,buf);gl.bufferData(gl.ARRAY_BUFFER,d,gl.DYNAMIC_DRAW);
  gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE_MINUS_SRC_ALPHA);use('spider.frag');f1('uAspect',A);f1('uStyle',0);f2('uShadowOffset',0.25,-0.3);
  f1('uShadowPass',1);gl.drawArraysInstanced(gl.TRIANGLE_STRIP,0,4,N);f1('uShadowPass',0);gl.drawArraysInstanced(gl.TRIANGLE_STRIP,0,4,N);gl.disable(gl.BLEND);F.push(snap());}return F;`,
fireworks: `const trail=target(W,H,0);bind(trail);clear();const sparks=[];const dt=1/15,F=[];const cols=[[1,0.15,0.1],[1,0.95,0.9],[0.25,0.4,1],[1,0.75,0.2]];
 function burst(x,y){const c=cols[(rnd()*4)|0],c2=cols[(rnd()*4)|0];const n=140;const ring=rnd()<0.3;for(let i=0;i<n;i++){const a=ring?i*6.283/n:rnd()*6.283,s=ring?0.4:0.45*(0.4+0.6*Math.sqrt(rnd()));sparks.push({x,y,vx:Math.cos(a)*s,vy:Math.sin(a)*s,age:0,life:1.4+rnd()*0.6,c:i%3?c:c2,gl:rnd()<0.3?rnd():-1});}}
 for(let f=0;f<72;f++){const w=walk(f/71);if(f%6==0)burst(w[0]*A,w[1]);if(f%10==3)burst((0.2+0.6*rnd())*A,0.2+0.6*rnd());
  bind(trail);gl.enable(gl.BLEND);use('solid.frag');gl.blendFunc(gl.ZERO,gl.SRC_ALPHA);gl.uniform4f(u('uColor'),0,0,0,Math.exp(-dt*5));fsq();
  gl.blendEquation(gl.FUNC_REVERSE_SUBTRACT);gl.blendFunc(gl.ONE,gl.ONE);gl.uniform4f(u('uColor'),1.5/255,1.5/255,1.5/255,0);fsq();gl.blendEquation(gl.FUNC_ADD);
  for(const s of sparks){s.age+=dt;const k=Math.exp(-2.2*dt);s.vx*=k;s.vy*=k;s.x+=s.vx*dt;s.y+=s.vy*dt;}
  gl.blendFunc(gl.ONE,gl.ONE);sprites(sparks.filter(s=>s.age<s.life).map(s=>{const t=s.age/s.life,hot=Math.exp(-s.age*8),fd=(1-t)*(1-t);return[s.x,s.y,0,0.015*(1+hot),s.c[0]+(1-s.c[0])*hot,s.c[1]+(1-s.c[1])*hot,s.c[2]+(1-s.c[2])*hot,fd,0.6,s.gl,0.25,0];}),'spark.frag',f/15);
  gl.disable(gl.BLEND);bind(null);use('copy.frag');tex('uSource',0,trail.t);fsq();F.push(snap());}return F;`,
christmas: `const cs=200,cw=Math.round(cs*A);const cov=pp(cw,cs,1);for(const t of [cov.a,cov.b]){bind(t);clear(0.85,0,0,1);}const F=[];
 const fl=[];for(let i=0;i<220;i++)fl.push({x:rnd()*A,y:rnd(),z:rnd(),s:0.6+rnd()*0.8,sd:rnd()});
 for(let f=0;f<72;f++){const w=walk(f/71);const side=(f%6<3)?1:-1;const nx=-w[3],ny=w[2]*A;const nl=Math.hypot(nx,ny)||1;
  bind(cov.b);use('snow_cover.frag');tex('uState',0,cov.a.t);f2('uTexel',1/cw,1/cs);f1('uAspect',A);const st=new Float32Array(64);st.set([w[0]+side*nx/nl*0.02/A,w[1]+side*ny/nl*0.02,0.022,0.5]);
  i1('uStampCount',f%3==0?1:0);gl.uniform4fv(u('uStamps'),st);f1('uFill',0);f1('uSoften',0);fsq();cov.swap();
  bind(null);clear();use('snow_ground.frag');common(f/12,[]);tex('uCover',0,cov.a.t);f2('uCoverTexel',1/cw,1/cs);f1('uTheme',1);f1('uLights',1);f1('uNight',5);fsq();
  for(const k of fl){k.z-=0.012;k.x+=0.004*(0.5+k.z)+Math.sin(f*0.3+k.sd*40)*0.002;if(k.z<=0){k.z=1;k.x=rnd()*A;k.y=rnd();}}
  const L=fl.map(k=>{const fd=Math.min(1,k.z/0.06)*Math.min(1,(1-k.z)/0.08);return[k.x,k.y,k.sd*6+f*0.02,0.007*k.s*(0.6+1.6*k.z),1,1,1,fd*0.95,Math.min(1,Math.max(0,(k.s-0.9)*2))*(1-k.z*0.5),k.z,0,k.z];});
  gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE_MINUS_SRC_ALPHA);sprites(L,'snowflake.frag',0,()=>{f2('uOffset',1.2,-1.6);f1('uShadow',1);});sprites(L,'snowflake.frag',0,()=>{f1('uShadow',0);});gl.disable(gl.BLEND);F.push(snap());}return F;`,
garden: `const cs=200,cw=Math.round(cs*A);const g=pp(cw,cs,1);for(const t of [g.a,g.b]){bind(t);clear(0,0,0,1);}const F=[];
 for(let f=0;f<84;f++){for(let k=0;k<3;k++){const step=f*3+k;bind(g.b);use('garden_growth.frag');tex('uState',0,g.a.t);f1('uAspect',A);const st=new Float32Array(64);let n=0;
   if(step<170){const w=walk(step/170);st.set([w[0],w[1],0.07,0.2]);n=1;}i1('uStampCount',n);gl.uniform4fv(u('uStamps'),st);f1('uWilt',0);f1('uGrow',0.35/30*1.4);f1('uShrink',0.2/30);fsq();g.swap();}
  bind(null);clear();use('garden.frag');common(f/12,[]);tex('uGrowth',0,g.a.t);f1('uDensity',0.6);f1('uFlowerSize',1.1);f1('uGrass',0.8);fsq();F.push(snap());}return F;`,
pool: `const rs=200,rw=Math.round(rs*A);const rp=pp(rw,rs,1);for(const t of [rp.a,rp.b]){bind(t);clear(0,0,0,1);}const F=[];const drops=[];
 for(let f=0;f<72;f++){const w=walk(f/71);const ds=[];if(f==4){ds.push([w[0],w[1],0.05,0.9]);for(let i=0;i<40;i++){const a=rnd()*6.283,v=0.15+rnd()*0.35;drops.push({x:w[0]*A,y:w[1],vx:Math.cos(a)*v,vy:Math.sin(a)*v,h:0.01,vh:0.5+rnd()*0.6,s:0.004+rnd()*0.004});}}
  if(f%2==0&&f>4)ds.push([w[0],w[1],0.016,0.35]);
  for(const d of drops){if(d.h<=0)continue;d.vh-=3/15;d.h+=d.vh/15;d.x+=d.vx/15;d.y+=d.vy/15;if(d.h<=0&&ds.length<20)ds.push([d.x/A,d.y,0.008,0.15]);}
  ripple(rp,ds.slice(0,24),0);ripple(rp,[],0);
  bind(null);clear();use('pool.frag');common(f/12,[]);f1('uFlowTime',f/12);f1('uSun',1);f1('uRippleEncoded',0);f2('uRippleTexel',1/rw,1/rs);tex('uRipple',0,rp.a.t);fsq();
  gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE);sprites(drops.filter(d=>d.h>0).map(d=>[d.x,d.y,0,d.s*(1+d.h*4),0.85,0.95,1,0.9,0.7,-1,0.15,0]),'spark.frag');gl.disable(gl.BLEND);F.push(snap());}return F;`,
water: `const rs=200,rw=Math.round(rs*A);const rp=pp(rw,rs,1);for(const t of [rp.a,rp.b]){bind(t);clear(0,0,0,1);}const shapes=target(W/2,H/2,0);const F=[];
 for(let f=0;f<84;f++){const t=f/83;const w=[0.3+0.1*t,0.45,0,0];const em=Math.min(1,Math.max(0,(f-6)/40));const em2=Math.min(1,Math.max(0,(f-30)/40));
  const ds=[];if(f%3==0)ds.push([w[0],w[1],0.016,0.3]);if(f>6&&f<46&&f%4==0)ds.push([0.62+0.05*rnd(),0.45+0.1*(rnd()-.5),0.015,0.25]);ripple(rp,ds,0);ripple(rp,[],0);
  bind(shapes);clear();use('water_shapes.frag');common(f/12,[w]);gl.uniform4fv(u('uMonA'),new Float32Array([0.62*A,0.47,Math.PI,em*em*(3-2*em), 0.2*A,0.75,-0.4,em2*em2*(3-2*em2),0,0,0,0,0,0,0,0]));
  gl.uniform4fv(u('uMonB'),new Float32Array([0,12.3,f/12,0.3, 1,4.2,f/12,0.28, 0,0,0,0,0,0,0,0]));i1('uMonCount',2);
  gl.uniform4fv(u('uEyes'),new Float32Array([1.5,0.2,Math.min(1,f/20),0.022, 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]));i1('uEyeCount',1);fsq();
  bind(null);clear();use('water.frag');common(f/12,[w]);f1('uFlowTime',f/12);f1('uCaustics',1);f1('uMonsterGlow',1);f1('uRippleEncoded',0);tex('uRipple',0,rp.a.t);tex('uShapes',1,shapes.t);fsq();F.push(snap());}return F;`,
};

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: W, height: H } });
  page.on('pageerror', e => console.log('[pageerror]', e.message));
  await page.setContent(`<canvas id=c width=${W} height=${H}></canvas>`);
  await page.evaluate(([s, setup]) => { window.SRC = s; (0, eval)(setup); }, [src, SETUP]);
  fs.mkdirSync(outDir, { recursive: true });
  for (const [name, body] of Object.entries(SCENES)) {
    if (only && only !== name) continue;
    const frames = await page.evaluate(b => (new Function(b))(), body);
    const d = path.join(outDir, name); fs.mkdirSync(d, { recursive: true });
    frames.forEach((f, i) => fs.writeFileSync(path.join(d, String(i).padStart(3, '0') + '.png'), Buffer.from(f.split(',')[1], 'base64')));
    console.log(name, frames.length, 'frames');
  }
  await browser.close();
})();
