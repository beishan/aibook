import json,time,urllib.request,urllib.error,urllib.parse,os,hashlib,uuid
from pathlib import Path
base=os.environ.get('AUDIO_E2E_BASE_URL','http://127.0.0.1:58086').rstrip('/')
if os.environ.get('AUDIO_E2E_DISPOSABLE') != '1' or urllib.parse.urlparse(base).hostname not in ('localhost','127.0.0.1'):
    raise SystemExit('仅允许显式 AUDIO_E2E_DISPOSABLE=1 的本机隔离环境；本脚本会创建测试账户和数据。')
output=Path(os.environ.get('AUDIO_E2E_OUTPUT','/tmp/aibook-audio-e2e')); output.mkdir(parents=True,exist_ok=True)
def request(method,path,data=None,token=None,headers=None):
    h=dict(headers or {})
    if token:h['Authorization']='Bearer '+token
    if data is not None:h['Content-Type']='application/json'
    r=urllib.request.Request(base+path,data=json.dumps(data).encode() if data is not None else None,headers=h,method=method)
    try:
        with urllib.request.urlopen(r,timeout=60) as response:
            body=response.read(); return response.status,dict(response.headers),json.loads(body) if 'application/json' in response.headers.get('Content-Type','') and body else body
    except urllib.error.HTTPError as e:
        body=e.read()
        try:body=json.loads(body)
        except:pass
        return e.code,dict(e.headers),body
s,h,a=request('POST','/api/auth/register',{'username':'audiotest','email':'audio@example.test','password':'AudioTest123!','nickname':'音频测试'})
if s!=200:
    s,h,a=request('POST','/api/auth/login',{'username':'audiotest','password':'AudioTest123!'})
assert s==200,(s,a)
token=a['token']; (output/'auth.json').write_text(json.dumps(a));os.chmod(output/'auth.json',0o600)
s,h,d=request('POST','/api/scan-directories',{'path':'/audio-fixtures'},token)
if s!=200:
    s,h,dirs=request('GET','/api/scan-directories',token=token);d=next(x for x in dirs if x['path']=='/audio-fixtures')
s,h,task=request('POST','/api/audiobooks/imports/scan',{'directoryId':d['id'],'relativePath':'三体'},token)
assert s==202,(s,task)
for _ in range(40):
    s,h,task=request('GET','/api/audiobooks/imports/'+task['id'],token=token)
    if task['state']!='SCANNING':break
    time.sleep(.5)
assert task['state']=='READY',task
assert len(task['candidates'])==2,task
assert not any(c['error'] for c in task['candidates']),task
s,h,imported=request('POST','/api/audiobooks/imports/confirm',{'taskId':task['id'],'paths':[c['path'] for c in task['candidates']],'edition':{'title':'三体测试','author':'测试作者','name':'测试完整版','narrator':'测试演播者','language':'中文','description':'真实音频测试'}},token)
assert s==200,(s,imported)
id=imported['editionId']
s,h,detail=request('GET',f'/api/audiobooks/{id}',token=token);assert s==200,(s,detail)
assert len(detail['tracks'])==2 and len(detail['chapters'])==3,detail
assert 'filePath' not in detail['tracks'][0]
s,h,listed=request('GET','/api/audiobooks',token=token); assert s==200,(s,listed)
s,h,versions=request('GET',f"/api/books/{detail['bookId']}/versions",token=token);assert s==200 and versions==[],(s,versions)
s,h,session=request('POST',f'/api/audiobooks/{id}/sessions',{'takeover':False},token);assert s==200,(s,session)
track=detail['tracks'][0]
media=f"/api/audio-media/{session['sessionId']}/{track['id']}?key={session['key']}"
s,h,body=request('GET',media,headers={'Range':'bytes=0-15'});assert s==206 and len(body)==16,(s,h)
s,h,body=request('GET',media,headers={'Range':'bytes=999999999-'});assert s==416,(s,body)
s,h,body=request('GET',media.replace(session['key'],'invalid'));assert s in (401,403,404),(s,body)
event={'sequence':0,'revision':session['progress']['revision'],'contentRevision':session['contentRevision'],'trackId':track['id'],'positionMs':1000,'elapsedMs':0,'finished':False}
s,h,p=request('PUT',f"/api/audiobooks/sessions/{session['sessionId']}",event,token);assert s==200,(s,p)
s,h,p2=request('PUT',f"/api/audiobooks/sessions/{session['sessionId']}",event,token);assert s==200 and p2['revision']==p['revision'],(s,p2)
s,h,conflict=request('POST',f'/api/audiobooks/{id}/sessions',{'takeover':False},token);assert s==409,(s,conflict)
s,h,takeover=request('POST',f'/api/audiobooks/{id}/sessions',{'takeover':True},token);assert s==200,(s,takeover)
event.update(sequence=1,revision=p['revision'])
s,h,old=request('PUT',f"/api/audiobooks/sessions/{session['sessionId']}",event,token);assert s in (404,409),(s,old)
s,h,bookmark=request('POST',f'/api/audiobooks/{id}/bookmarks',{'trackId':track['id'],'positionMs':1500,'title':'书签测试'},token);assert s==200,(s,bookmark)
s,h,manifest=request('GET',f'/api/audiobooks/{id}/offline-manifest',token=token);assert s==200 and manifest['tracks'][0]['fileHash'],(s,manifest)
(output/'result.json').write_text(json.dumps({'editionId':id,'bookId':detail['bookId'],'sessionId':takeover['sessionId']}))
# Verify account isolation using a second synthetic account in this disposable database.
s,h,other=request('POST','/api/auth/register',{'username':'audioother','email':'other@example.test','password':'AudioTest123!'},token)
if s!=200:
    s,h,other=request('POST','/api/auth/login',{'username':'audioother','password':'AudioTest123!'})
assert s==200,(s,other)
other_token=other['token']
for endpoint in [f'/api/audiobooks/{id}',f'/api/audiobooks/imports/{task["id"]}',f'/api/audiobooks/{id}/offline-manifest',f'/api/audiobooks/{id}/tracks/{track["id"]}/download']:
    s,h,body=request('GET',endpoint,token=other_token)
    assert s in (403,404),(endpoint,s,body)
s,h,downloaded=request('GET',f'/api/audiobooks/{id}/tracks/{track["id"]}/download',token=token)
assert s==200 and hashlib.sha256(downloaded).hexdigest()==manifest['tracks'][0]['fileHash']
s,h,_=request('DELETE',f'/api/audiobooks/sessions/{takeover["sessionId"]}',token=token);assert s==200
s,h,current=request('GET',f'/api/audiobooks/{id}',token=token)
now=int(time.time()*1000)
offline={'eventId':str(uuid.uuid4()),'baseRevision':current['progress']['revision'],'contentRevision':current['contentRevision'],'trackId':track['id'],'positionMs':3000,'finished':False,'resolveConflict':False,'intervals':[{'startMs':now-5000,'endMs':now-1000}]}
s,h,applied=request('POST',f'/api/audiobooks/{id}/offline-progress',offline,token);assert s==200,(s,applied)
s,h,replay=request('POST',f'/api/audiobooks/{id}/offline-progress',offline,token);assert s==200 and replay['revision']==applied['revision'],(s,replay)
stale={**offline,'eventId':str(uuid.uuid4()),'positionMs':1000}
s,h,rejected=request('POST',f'/api/audiobooks/{id}/offline-progress',stale,token);assert s==409,(s,rejected)
s,h,stats=request('GET','/api/audiobooks/activity',token=token);assert s==200 and stats,(s,stats)
s,h,_=request('DELETE',f'/api/audiobooks/{id}',token=token);assert s==200
s,h,_=request('GET',f'/api/audiobooks/{id}',token=token);assert s==404
s,h,recycled=request('GET','/api/audiobooks?status=RECYCLED',token=token);assert s==200 and any(x['id']==id for x in recycled['content']),(s,recycled)
s,h,_=request('POST',f'/api/audiobooks/{id}/restore',token=token);assert s==200
s,h,_=request('GET',f'/api/audiobooks/{id}',token=token);assert s==200
print('PASS: 真实MP3/M4B探测、章节、纯有声作品、Range、错误凭证、重复心跳、接管、书签、跨账户隔离、下载SHA256、离线幂等与冲突、统计、回收恢复')
