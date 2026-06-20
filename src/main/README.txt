
   _____ ____  ____  ___ 
  / ___// __ \/ __ \/   |
  \__ \/ / / / /_/ / /| |
 ___/ / /_/ / _, _/ ___ |
/____/\____/_/ |_/_/  |_|
       Signal-Oriented Runtime Agent




========================
Database Module (DBM)
========================

========================
Config Module
========================

ConfigManager.initialize()
- Return: boolean
- Purpose: Load runtime config into memory at startup; false means defaults/fallback were used.

ConfigManager.reload()
- Return: boolean
- Purpose: Re-read runtime config from disk into memory using the same validation/fallback rules.

ConfigManager.isInitialized()
- Return: boolean
- Purpose: Check whether runtime config has been initialized in this process.

ConfigManager.wasLoadedFromFile()
- Return: boolean
- Purpose: Check whether the most recent config load succeeded from the on-disk file.

ConfigManager.getConfigPath()
- Return: Path
- Purpose: Get the canonical runtime config file path.

ConfigManager.snapshot()
- Return: JSONObject
- Purpose: Get a defensive copy of the full in-memory runtime config root.

ConfigManager.snapshotValues()
- Return: JSONObject
- Purpose: Get a defensive copy of the in-memory `values` object only.

ConfigManager.getVersion()
- Return: int
- Purpose: Get the runtime config schema version currently stored in memory.

ConfigManager.getValue(String valuePath)
- Return: Object
- Purpose: Read a config value by dot path under `values`; JSON containers are defensive copies.

ConfigManager.getString(String valuePath, String fallback)
- Return: String
- Purpose: Read a string config value with coded fallback.

ConfigManager.getBoolean(String valuePath, boolean fallback)
- Return: boolean
- Purpose: Read a boolean config value with coded fallback.

ConfigManager.getInt(String valuePath, int fallback)
- Return: int
- Purpose: Read an int config value with coded fallback.

ConfigManager.getLong(String valuePath, long fallback)
- Return: long
- Purpose: Read a long config value with coded fallback.

ConfigManager.getDouble(String valuePath, double fallback)
- Return: double
- Purpose: Read a double config value with coded fallback.

ConfigManager.getStringList(String valuePath, List<String> fallback)
- Return: List<String>
- Purpose: Read a string-list config value with coded fallback.

ConfigManager.getFlags(String valuePath)
- Return: int
- Purpose: Read the bitmap flags for a config leaf.

ConfigManager.doesNotUpdateRuntime(String valuePath)
- Return: boolean
- Purpose: Check whether the config leaf is marked as non-live-updating.

ConfigManager.requiresExplicitApply(String valuePath)
- Return: boolean
- Purpose: Check whether the config leaf is marked as requiring explicit manual apply.

ConfigManager.setValueInMemory(String valuePath, Object value)
- Return: void
- Purpose: Change a runtime config value in memory only; does not write to disk or apply module updates.

ConfigManager.setFlagsInMemory(String valuePath, int flags)
- Return: void
- Purpose: Change a runtime config leaf's flags bitmap in memory only.

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

readJSONObject(String filePath)
- Return: JSONObject
- Purpose: Read the full validated JSON object from cached JSON as a defensive copy.

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

========================
Registry Editor
========================

readUserData(String fileName, String jsonPath)
- Return: Object
- Purpose: Read a value from a user JSON file.

writeUserData(String fileName, String jsonPath, Object value)
- Return: boolean
- Purpose: Write a value to a user JSON file.

readServerData(String fileName, String jsonPath)
- Return: Object
- Purpose: Read a value from a server JSON file.

writeServerData(String fileName, String jsonPath, Object value)
- Return: boolean
- Purpose: Write a value to a server JSON file.

readGlobalData(String fileName, String jsonPath)
- Return: Object
- Purpose: Read a value from a global JSON file.

writeGlobalData(String fileName, String jsonPath, Object value)
- Return: boolean
- Purpose: Write a value to a global JSON file.

readRegistryData(String fileName, String jsonPath)
- Return: Object
- Purpose: Read a value from a registry JSON file.

writeRegistryData(String fileName, String jsonPath, Object value)
- Return: boolean
- Purpose: Write a value to a registry JSON file.

deleteUserFile(String fileName)
- Return: boolean
- Purpose: Delete a user JSON file.

deleteUserPath(String fileName, String jsonPath)
- Return: boolean
- Purpose: Delete a path inside a user JSON file.

deleteServerFile(String fileName)
- Return: boolean
- Purpose: Delete a server JSON file.

deleteServerPath(String fileName, String jsonPath)
- Return: boolean
- Purpose: Delete a path inside a server JSON file.

deleteGlobalFile(String fileName)
- Return: boolean
- Purpose: Delete a global JSON file.

deleteGlobalPath(String fileName, String jsonPath)
- Return: boolean
- Purpose: Delete a path inside a global JSON file.

deleteRegistryFile(String fileName)
- Return: boolean
- Purpose: Delete a registry JSON file.

deleteRegistryPath(String fileName, String jsonPath)
- Return: boolean
- Purpose: Delete a path inside a registry JSON file.

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
Task Scheduler Module
========================

TaskScheduler
start(TaskScheduler.CommandExecutor executor)
- Return: boolean
- Purpose: Start the scheduler worker (true if started, false if already running).

shutdown()
- Return: boolean
- Purpose: Stop the scheduler worker and wait for it to exit.

shutdownNoExit()
- Return: boolean
- Purpose: Fast shutdown without waiting for the worker to exit.

scheduleRequest(TaskRequest request)
- Return: String (taskId)
- Purpose: Validate, persist, and enqueue a TaskRequest.

pause(String taskId)
- Return: void
- Purpose: Pause a scheduled task by id.

resume(String taskId)
- Return: void
- Purpose: Resume a paused task by id.

cancel(String taskId)
- Return: void
- Purpose: Cancel a task by id (removes from registry).

TaskRequest
TaskRequest()
- Return: TaskRequest
- Purpose: Create an empty request for configuration.

setName(String name)
- Return: TaskRequest
- Purpose: Set the display name.

setPriority(int priority)
- Return: TaskRequest
- Purpose: Set priority (lower is higher priority).

setOpKey(ExeWhitelist.OpKey opKey)
- Return: TaskRequest
- Purpose: Set the whitelisted operation key.

setOpArgs(String opArgs)
- Return: TaskRequest
- Purpose: Set comma-separated operation arguments.

setRetries(int retries)
- Return: TaskRequest
- Purpose: Set retry attempts after the first failure.

setType(ScheduledTask.Type type)
- Return: TaskRequest
- Purpose: Set schedule type (prefer specific type setters).

setExecuteAt(Long executeAt)
- Return: TaskRequest
- Purpose: Set absolute execution time (ms).

setIntervalMs(Long intervalMs)
- Return: TaskRequest
- Purpose: Set interval duration (ms).

setDelayMs(Long delayMs)
- Return: TaskRequest
- Purpose: Set uptime delay (ms).

setAbsoluteOnce(long executeAt)
- Return: TaskRequest
- Purpose: Configure a one-shot absolute time.

setAbsoluteInterval(long executeAt, long intervalMs)
- Return: TaskRequest
- Purpose: Configure a wall-clock interval schedule.

setUptimeDelay(long delayMs)
- Return: TaskRequest
- Purpose: Configure a one-shot delay from scheduler boot.

setUptimeInterval(Long delayMsOrNull, long intervalMs)
- Return: TaskRequest
- Purpose: Configure a boot-anchored interval schedule.

lock()
- Return: TaskRequest
- Purpose: Validate fields and lock the request.

isLocked()
- Return: boolean
- Purpose: Whether the request is locked.

getName()
- Return: String
- Purpose: Get the display name.

getType()
- Return: ScheduledTask.Type
- Purpose: Get the schedule type.

getPriority()
- Return: int
- Purpose: Get priority.

getOpKey()
- Return: String
- Purpose: Get normalized operation key.

getOpArgs()
- Return: String
- Purpose: Get operation arguments.

getRetries()
- Return: int
- Purpose: Get retry attempts.

getExecuteAt()
- Return: Long
- Purpose: Get absolute execution time.

getIntervalMs()
- Return: Long
- Purpose: Get interval duration.

getDelayMs()
- Return: Long
- Purpose: Get uptime delay.

TaskExecutor
execute(String opKey, String opArgs)
- Return: TaskResult
- Purpose: Resolve opKey, parse args, and run the whitelist handler.

listOpKeys()
- Return: Set<String>
- Purpose: List whitelisted operation keys.

ExeWhitelist
OpKey (enum)
- Purpose: Whitelisted operation keys.

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
