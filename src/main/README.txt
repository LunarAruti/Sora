
   _____ ____  ____  ___ 
  / ___// __ \/ __ \/   |
  \__ \/ / / / /_/ / /| |
 ___/ / /_/ / _, _/ ___ |
/____/\____/_/ |_/_/  |_|
       Signal-Oriented Runtime Agent


========================
Commit Info
========================

- Buffered Logger (single worker, ring cap, shutdown wait), ShutdownManager added.
- DependencyManager made standalone (direct FS only) and moved earlier in startup.
- QueueManager gained explicit shutdown (idempotent); shutdown hook delegates to it.
- Network module stabilized (temp outputs, collision handling, error objects, journals/diagnostics dumps).
- Parser upgrades + README expanded with full method lists/status codes.

This document compiles the method lists from:
- database/methods.txt
- network/methods.txt
- network/status_codes.txt

The goal is a quick, plain-language reference for what each method does and
what it returns. "Return" notes reflect current code.

========================
Missing Features
========================

- Network Module does not automatically enforce async requests

========================
Database Module (DBM)
========================

initialize()
- Return: void
- Purpose: Initialize DB structure and logging hooks.

createFolder(String path)
- Return: String (normalized absolute path)
- Purpose: Create folder and parents if needed.

folderExists(String path)
- Return: boolean
- Purpose: Check folder existence and accessibility.

deleteFolder(String path)
- Return: boolean
- Purpose: Delete an empty folder.

createFile(String path)
- Return: String (normalized absolute path)
- Purpose: Create a file (parents created if needed).

fileExists(String path)
- Return: boolean
- Purpose: Check file existence and accessibility.

deleteFile(String path)
- Return: boolean
- Purpose: Delete a file.

renameFile(String oldPath, String newPath)
- Return: boolean
- Purpose: Rename/move a file.

copyFile(String sourcePath, String destPath)
- Return: boolean
- Purpose: Copy a file.

makeTemporary(String path)
- Return: boolean
- Purpose: Mark cached entry as TEMP (cache-only). False if none changed or disk entry exists.

makePermanent(String path)
- Return: boolean
- Purpose: Mark cached entry as PERMANENT (cache-only). False if none changed.

listFiles(String folderPath)
- Return: List<String>
- Purpose: List files in a folder.

listFolders(String folderPath)
- Return: List<String>
- Purpose: List subfolders in a folder.

getExtension(String path)
- Return: String
- Purpose: File extension (without dot).

getParentPath(String path)
- Return: String
- Purpose: Parent folder path.

getFileName(String path)
- Return: String
- Purpose: File name only.

createJSON(String path)
- Return: String (normalized absolute path)
- Purpose: Create a JSON file (empty object).

createJSON(String path, JSONObject defaultContent)
- Return: String (normalized absolute path)
- Purpose: Create JSON file with default content.

ensureJSONIntegrity(String path, boolean enforceObject, boolean autoRepair)
- Return: IntegrityReport
- Purpose: Validate JSON file and optionally repair.

readJSONPath(String filePath, String jsonPath)
- Return: Object
- Purpose: Read value at JSON path from cached JSON.

writeJSONPath(String filePath, String jsonPath, Object value, boolean createMissing)
- Return: boolean
- Purpose: Write value to JSON path in cache (batch write).

removeJSONPath(String filePath, String jsonPath)
- Return: boolean
- Purpose: Remove value at JSON path in cache.

containsJSONPath(String filePath, String jsonPath)
- Return: boolean
- Purpose: Check if JSON path exists.

appendJSONArray(String filePath, String jsonPath, Object value)
- Return: boolean
- Purpose: Append a value to a JSON array.

countJSONArray(String filePath, String jsonPath)
- Return: int
- Purpose: Count elements in a JSON array.

listJSONKeys(String filePath, String jsonPath)
- Return: List<String>
- Purpose: List keys at JSON object path.

getTypeAtPath(String filePath, String jsonPath)
- Return: String
- Purpose: Return the JSON type at a path (object/array/primitive).

renameJSONKey(String filePath, String parentPath, String oldKey, String newKey)
- Return: boolean
- Purpose: Rename a key in a JSON object.

moveJSONPath(String filePath, String fromPath, String toPath)
- Return: boolean
- Purpose: Move a JSON node from one path to another.

sanitizeJSON(String filePath, boolean fixArrays)
- Return: boolean
- Purpose: Sanitize JSON structure (repair invalid nodes).

pathExistsOrThrow(JSONObject root, String jsonPath)
- Return: Object
- Purpose: Resolve JSON path or throw if missing.

printJSONTree(String filePath)
- Return: void
- Purpose: Print JSON tree to log.

buildJSONTree(String filePath, boolean compact)
- Return: String
- Purpose: Build a string tree view of JSON.

clearJSONArray(String filePath, String jsonPath)
- Return: boolean
- Purpose: Clear array at JSON path.

clearJSONObject(String filePath, String jsonPath)
- Return: boolean
- Purpose: Clear object at JSON path.

insertJSONArray(String filePath, String jsonPath, int index, Object value)
- Return: boolean
- Purpose: Insert value into JSON array at index.

replaceJSONArray(String filePath, String jsonPath, int index, Object value)
- Return: boolean
- Purpose: Replace array element at index.

findJSONArray(String filePath, String jsonPath, String keyName, Object targetValue)
- Return: Object
- Purpose: Find first array element with key=value.

copyJSONPath(String filePath, String fromPath, String toPath)
- Return: boolean
- Purpose: Copy JSON node from one path to another.

Internal / helper methods (not public API)
- sanitizeNode(Object node, boolean fixArrays)
- parseSegment(String token)
- tryParseJSON(String content, boolean enforceObject)
- traverseJSONNode(Object node, String prefix, StringBuilder sb, String indent)
- previewValue(Object val)
- ensureArraySize(JSONArray array, int targetIndex)
- clearJSONArray(JSONArray arr)
- clearJSONObject(JSONObject obj)
- readJSONRaw(String path)
- writeJSONRaw(String path, JSONObject data)
- moveToCorrupt(String path)

========================
Network Module
========================

NetworkManager
start()
- Return: boolean
- Purpose: Start network workers (true if started, false if already running).

start(int workerCount)
- Return: boolean
- Purpose: Start with custom worker count (true if started, false if already running).

shutdown()
- Return: boolean
- Purpose: Stop workers and reject new requests (true if shutdown initiated).

request(NetworkRequest request)
- Return: boolean
- Purpose: Enqueue or coalesce request; false if rejected (shutdown/queue full).

requestAndReturnCachePath(NetworkRequest request)
- Return: String
- Purpose: Enqueue request and return the DBM cache path for results.

getDiagnosticsSummary()
- Return: Map<String,Object>
- Purpose: Snapshot of network diagnostics and counters.

dumpDiagnosticsToTemp()
- Return: String
- Purpose: Write diagnostics JSON to a temp file and return its path.

dumpJournalToTemp()
- Return: String
- Purpose: Write journal JSON to a temp file and return its path.

NetworkJournal
record(NetworkResult result, NetworkException.ErrorType errorType)
- Return: boolean
- Purpose: Append a completed request to the in-memory journal.

snapshot()
- Return: List<JournalEntry>
- Purpose: Snapshot of recent completions (oldest to newest).

clear()
- Return: boolean
- Purpose: Clear the in-memory journal (true if entries existed).

NetworkConfig
addWhitelistedHost(String host)
- Return: boolean
- Purpose: Add host to whitelist (true if changed).

addBlacklistedHost(String host)
- Return: boolean
- Purpose: Add host to blacklist (true if changed).

removeWhitelistedHost(String host)
- Return: boolean
- Purpose: Remove host from whitelist (true if changed).

removeBlacklistedHost(String host)
- Return: boolean
- Purpose: Remove host from blacklist (true if changed).

getWhitelistedHosts()
- Return: Set<String>
- Purpose: Snapshot of whitelist.

getBlacklistedHosts()
- Return: Set<String>
- Purpose: Snapshot of blacklist.

isHostAllowed(String host)
- Return: boolean
- Purpose: Check allow/deny rules for a host.

setDefaultRateBucket(String key, String bucket)
- Return: boolean
- Purpose: Set default bucket for a service/host (true if changed).

removeDefaultRateBucket(String key)
- Return: boolean
- Purpose: Remove default bucket mapping (true if removed).

getDefaultRateBucket(String key)
- Return: String
- Purpose: Get default bucket for service/host.

getAllDefaultRateBuckets()
- Return: Map<String,String>
- Purpose: Snapshot of all rate bucket mappings.

setDefaultMaxResponseBytes(String key, Long maxBytes)
- Return: boolean
- Purpose: Set/remove default max bytes for service/host.

getDefaultMaxResponseBytes(String key)
- Return: Long
- Purpose: Get default max bytes for service/host.

getAllDefaultMaxResponseBytes()
- Return: Map<String,Long>
- Purpose: Snapshot of all default max byte limits.

setGlobalMaxResponseBytes(Long maxBytes)
- Return: boolean
- Purpose: Set/remove global max response size (true if changed).

getGlobalMaxResponseBytes()
- Return: Long
- Purpose: Get global max response size.

computeEffectiveMaxResponseBytes(String key)
- Return: Long
- Purpose: Get effective max bytes after global/service defaults.

logSummary()
- Return: void
- Purpose: Log config summary (whitelist/blacklist/buckets/limits).

RateLimiterRegistry
configureBucket(String bucketName, double capacity, double refillPerSecond)
- Return: boolean
- Purpose: Create/update token bucket (true if changed).

tryAcquire(String bucketName)
- Return: boolean
- Purpose: Acquire one token from bucket.

tryAcquire(String bucketName, int permits)
- Return: boolean
- Purpose: Acquire multiple tokens from bucket.

estimateDelayMillis(String bucketName, int permits)
- Return: long
- Purpose: Estimate delay until permits are available.

snapshot()
- Return: Map<String, RateLimiterSnapshot>
- Purpose: Snapshot of all rate limiter buckets.

CircuitBreakerRegistry
allowRequest(String key, long nowMillis)
- Return: boolean
- Purpose: Check if circuit allows a new request.

getRemainingOpenMillis(String key, long nowMillis)
- Return: long
- Purpose: Remaining time until OPEN circuit transitions to HALF_OPEN.

recordSuccess(String key)
- Return: boolean
- Purpose: Record success and close circuit (true if changed).

recordFailure(String key, long nowMillis)
- Return: boolean
- Purpose: Record failure and possibly open circuit (true if changed).

getState(String key)
- Return: CircuitState
- Purpose: Current circuit state for key.

snapshot()
- Return: Map<String, BreakerStateSnapshot>
- Purpose: Snapshot of circuit breaker states.

NRO (NetworkRequest)
NetworkRequest(String service, String name)
- Return: NetworkRequest
- Purpose: Create a new request object.

Builder / setters (all return NetworkRequest for chaining)
setTraceId(String id) - set trace id
setType(NetworkRequest.Type t) - set HTTP verb
setRequestUrl(String url) - set base URL
setPath(String p) - set URL path template
putVar(String key, Object val) - add template var
putVars(Map<String, ?> m) - add template vars
header(String k, String v) - add header
setJsonBody(JSONObject body) - set JSON body
setAuthBearer(Supplier<String> tokenProvider) - bearer auth
setAuthApiKey(String headerName, Supplier<String> keyProvider) - API key auth
setAuthCustom(BiConsumer<RequestDraft, Map<String, String>> signer) - custom signer
setTimeout(Duration d) - total timeout
setConnectTimeout(Duration d) - connect timeout
setReadTimeout(Duration d) - read timeout
setRateBucket(String b) - rate limiter bucket
setCircuitKey(String k) - circuit key override
setIdempotencyKey(String k) - idempotency key
setPriority(NetworkRequest.Priority p) - request priority
setRetryPolicy(NetworkRequest.RetryPolicy policy) - retry policy
setFailureMode(NetworkRequest.FailureMode mode) - failure handling
setResponseType(NetworkRequest.ResponseType rt) - expected JSON type
setProjectionPath(String p) - JSON projection path
setAcceptableStatusCodes(Set<Integer> codes) - treat as success
setTreatOtherStatusAsError(boolean treatAsError) - non-2xx error toggle
setReturnAlias(String a) - filename alias
setCachePath(String path) - output path
setMaxResponseBytes(Long maxBytes) - response size cap
setAllowedContentTypes(Set<String> types) - allowed content types
setFollowRedirects(boolean follow) - redirect handling
setMaxRedirects(int max) - redirect cap
setCollectMetrics(boolean collect) - include metadata in output
setDedupeKey(String key) - de-dupe key for queue

seal()
- Return: NetworkRequest
- Purpose: Validate request, render final URL/headers, lock config.

isSealed()
- Return: boolean
- Purpose: Whether request is sealed.

Getters (return current value)
getService()
getName()
getTraceId()
getType()
getFinalUrl()
getRenderedHeaders()
getJsonBody()
getResponseType()
getProjectionPath()
getReturnAlias()
getTimeout()
getConnectTimeout()
getReadTimeout()
getRateBucket()
getCircuitKey()
getIdempotencyKey()
getCachePath()
getPriority()
getRetryPolicy()
getFailureMode()
getAcceptableStatusCodes()
isTreatOtherStatusAsError()
getMaxResponseBytes()
getAllowedContentTypes()
isFollowRedirects()
getMaxRedirects()
isCollectMetrics()
getDedupeKey()

toCurl()
- Return: String
- Purpose: Build a curl command for the sealed request.

========================
Logger (Util)
========================

Logger.init()
- Return: void
- Purpose: Clear main log, ensure dump file, start writer thread.

Logger.log(Logger.TAG tag, String message)
- Return: void
- Purpose: Enqueue a log line (DUMP always writes; ignore list applied to others).

Logger.logDump(String message)
- Return: void
- Purpose: Convenience dump log.

Logger.logDump(String heading, Throwable t)
- Return: void
- Purpose: Dump an exception stack with a heading.

Logger.shutdown()
- Return: boolean
- Purpose: Begin shutdown and flush pending entries (false if already shutting down).

Logger.shutdownWait()
- Return: boolean
- Purpose: Begin shutdown and wait up to default timeout for writer to stop.

Logger.shutdownWait(long timeoutMs)
- Return: boolean
- Purpose: Begin shutdown and wait up to timeout (<=0 waits indefinitely).


ShutdownManager.shutdown(JDA jda)
- Return: void
- Purpose: Graceful shutdown sequence (network → batch → queue → JDA → logger).
