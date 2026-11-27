# Task Manager Service

A console-based Java task manager built with Maven. The project is intentionally
layered so that the API, business rules, persistence, query features, and LLM
integration can be worked on independently.

## Overview

Main capabilities:

- Create, read, update, and delete tasks.
- Create subtasks and list a task's subtasks.
- Delete a parent task together with its subtasks.
- Filter by completion status and priority.
- Case-insensitive search over title and description.
- Sort by `created` or `dueDate`, in `asc` or `desc` order.
- Paginate results (`page` starts at 1, `pageSize` defaults to 10).
- Idempotent creation using an optional `idempotencyKey`.
- AI task creation from free text.
- AI task breakdown into subtasks, with a rule-based offline fallback.
- A structured response shape for success and failure.

The default repository is in memory, so data is lost when the JVM exits.

## Package structure

```text
src/main/java/com/airtribe/taskmanager
├── App.java                       Entry point / small demo
├── ApiExamples.java               Prints one example per TaskApi operation
├── IdempotencyDemo.java           Proof that a retry does not duplicate a task
├── SubtaskDemo.java               Proof that deleting a parent deletes subtasks
├── api
│   ├── TaskApi.java               Public API surface (returns ApiResponse)
│   └── ErrorResponseFactory.java  Maps exceptions to error responses
├── service
│   ├── TaskService.java           Business rules and orchestration
│   ├── TaskQueryEngine.java       filter → search → sort → paginate pipeline
│   ├── IdempotencyStore.java      Singleton key/fingerprint/response store
│   ├── DateHelper.java            Date validation and "today"
│   ├── filter
│   │   ├── TaskFilter.java
│   │   ├── CompletedFilter.java
│   │   ├── PriorityFilter.java
│   │   ├── SearchFilter.java
│   │   └── TaskFilters.java       Default filter registry
│   ├── sort
│   │   ├── SortStrategy.java
│   │   ├── CreatedAtAscendingSort.java
│   │   ├── CreatedAtDescendingSort.java
│   │   ├── DueDateAscendingSort.java
│   │   ├── DueDateDescendingSort.java
│   │   └── TaskSorts.java         Default sort registry
│   └── breakdown
│       ├── BreakdownStrategy.java
│       ├── RuleBasedBreakdownStrategy.java
│       ├── LlmBreakdownStrategy.java
│       └── BreakdownRequestFactory.java
├── model
│   ├── Task.java                  Task + TaskBuilder
│   ├── TaskRequest.java           Input DTO for create/update
│   ├── TaskQuery.java             Optional list criteria + Builder
│   ├── Priority.java              LOW / MEDIUM / HIGH
│   ├── ApiResponse.java           status + body
│   └── ErrorDto.java              error code + message + details
├── repository
│   ├── TaskRepository.java
│   └── InMemoryTaskRepository.java
├── parser
│   ├── TaskParser.java
│   ├── LlmTaskParse.java
│   ├── LlmClient.java
│   ├── HttpLlmClient.java         anthropic / openai / gemini / deepseek
│   ├── ParsedTask.java
│   ├── SimpleJson.java
│   └── RuleBasedTaskParser.java   offline placeholder
├── exception
│   ├── ApiException.java          base with status + code
│   ├── ValidationException.java   400
│   ├── NotFoundException.java     404
│   ├── ConflictException.java     422
│   ├── BadGatewayException.java   502
│   ├── ServiceUnavailableException.java  503
│   └── FieldIssue.java
└── utils
    ├── Validator.java
    └── IdGenerator.java
```

## Setup

### Prerequisites

- **JDK 25** — `pom.xml` sets both `maven.compiler.source` and
  `maven.compiler.target` to `25`.
- **Apache Maven 3.9+**.

Check the versions:

```bash
java -version
mvn -version
```

### Compile and test

```bash
mvn clean test        # compile and run the tests
mvn clean package     # run tests and build the jar
mvn clean package -DskipTests
```

### Run `Main`

`App` is `com.airtribe.taskmanager.App`.

The easiest way to run it with all dependencies on the classpath is Maven's
exec plugin:

```bash
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.airtribe.taskmanager.App
```

If you prefer a plain `java` command, build the dependency classpath first.
The jar produced by `mvn package` is **not** a fat jar, so running it with only
`-cp target/task-manager-1.0-SNAPSHOT.jar` will fail to find Jackson.

PowerShell:

```powershell
mvn -q dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"
$cp = "target\classes;" + (Get-Content target\classpath.txt -Raw).Trim()
java -cp $cp com.airtribe.taskmanager.App
```

Bash / Git Bash:

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" com.airtribe.taskmanager.App
```

### Set `LLM_API_KEY`

The AI operations need a key. The current `App` wires the DeepSeek provider and
the `deepseek-flash` model:

```java
var llmClient = new HttpLlmClient(System.getenv("LLM_API_KEY"), "deepseek", "deepseek-flash");
```

Set the key in the current terminal session.

PowerShell:

```powershell
$env:LLM_API_KEY = "your-api-key"
```

Bash:

```bash
export LLM_API_KEY="your-api-key"
```

Persistent Windows setting (new terminals only):

```powershell
setx LLM_API_KEY "your-api-key"
```

Without a key, the non-AI operations still work; AI operations return `502`
because the provider call fails. To use another provider, change the constructor
arguments in `App` — `HttpLlmClient` supports `anthropic`, `openai`, `gemini`,
and `deepseek`.

### Runnable demos

```bash
# No key needed: uses a fake LLM client and prints all API examples
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.airtribe.taskmanager.ApiExamples

# No key needed: idempotency proof
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.airtribe.taskmanager.IdempotencyDemo

# No key needed: rule-based breakdown + cascade delete proof
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.airtribe.taskmanager.SubtaskDemo
```

## TaskApi operations

Every operation returns an `ApiResponse`:

```json
{"status":200,"body": ...}
```

Errors use the same envelope with an `ErrorDto` body:

```json
{
  "status":400,
  "body": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [{"field": "title", "issue": "Title cannot be null or empty"}]
  }
}
```

`idempotencyKey` is nullable everywhere it appears. A blank key is treated as
absent.

| Operation | Parameters | Success | Errors |
|---|---|---|---|
| `createTask` | `TaskRequest request`, `String idempotencyKey` | `201` + `Task` | `400` validation; `422` key reused with a different request; `500` unexpected |
| `getTask` | `int id` | `200` + `Task` | `404` not found; `500` unexpected |
| `createSubtask` | `int parentId`, `TaskRequest request` | `201` + child `Task` | `400` invalid request or parent is itself a subtask; `404` parent not found; `500` unexpected |
| `listSubtasks` | `int parentId` | `200` + `List<Task>` | `500` unexpected. An unknown parent returns an empty list. |
| `listTasks` | `TaskQuery query` | `200` + `List<Task>` | `400` invalid priority, `sortBy`, `order`, `page`, or `pageSize`; `500` unexpected |
| `updateTask` | `int id`, `TaskRequest request` | `200` + `Task` | `400` validation; `404` not found; `500` unexpected |
| `deleteTask` | `int id` | `200` + `Boolean` | `404` not found; `500` unexpected. Subtasks are deleted too. |
| `createTaskFromText` | `String text`, `String idempotencyKey` | `201` + `Task` | `400` parsed task fails validation; `422` key conflict; `502` LLM call/parse failure; `500` unexpected |
| `breakDownTask` | `int id`, `String idempotencyKey` | `200` + `List<Task>` | `400` task is itself a subtask; `404` task not found; `422` key conflict; `502` LLM call failure or any invalid item; `500` unexpected |

`listTasks(null)` returns every task without pagination; `listTasks(new TaskQuery())`
uses the defaults (`page=1`, `pageSize=10`).

`TaskQuery` fields: `completed`, `priority` (`LOW` / `MEDIUM` / `HIGH`),
`search`, `sortBy` (`created` / `dueDate`), `order` (`asc` / `desc`), `page`,
and `pageSize`.

### Examples

The calls and responses below match `ApiExamples`, which uses a fake LLM client so
they are reproducible offline. `createdAt` values change on every run; IDs are
sequential for a fresh in-memory repository. JSON is formatted here for
readability; the demo prints one line. The
`request(title, description, priority, dueDate)` helper is defined in
`ApiExamples`.

#### `createTask`

```java
var request = request("Buy milk", "2L cow milk", "HIGH", "2026-12-03");
api.createTask(request, "create-1");
```

```json
{
  "status": 201,
  "body": {
    "completed": false,
    "createdAt": 1795751505911,
    "description": "2L cow milk",
    "dueDate": "2026-12-03",
    "id": 1,
    "parentId": 0,
    "priority": "HIGH",
    "title": "Buy milk"
  }
}
```

#### `getTask`

```java
api.getTask(1);
```

```json
{
  "status": 200,
  "body": {
    "completed": false,
    "createdAt": 1795751505911,
    "description": "2L cow milk",
    "dueDate": "2026-12-03",
    "id": 1,
    "parentId": 0,
    "priority": "HIGH",
    "title": "Buy milk"
  }
}
```

#### `createSubtask`

```java
api.createSubtask(1, request("Buy bread", "Whole wheat bread", "LOW", "2026-12-01"));
```

```json
{
  "status": 201,
  "body": {
    "completed": false,
    "createdAt": 1795751506063,
    "description": "Whole wheat bread",
    "dueDate": "2026-12-01",
    "id": 2,
    "parentId": 1,
    "priority": "LOW",
    "title": "Buy bread"
  }
}
```

#### `listSubtasks`

```java
api.listSubtasks(1);
```

```json
{
  "status": 200,
  "body": [
    {
      "completed": false,
      "createdAt": 1795751506063,
      "description": "Whole wheat bread",
      "dueDate": "2026-12-01",
      "id": 2,
      "parentId": 1,
      "priority": "LOW",
      "title": "Buy bread"
    }
  ]
}
```

#### `listTasks` (search + sort + pagination)

```java
var query = TaskQuery.builder()
        .search("buy")          // case-insensitive title/description search
        .sortBy("dueDate")      // "created" or "dueDate"
        .order("asc")           // "asc" or "desc"
        .page(1)                // page 1 is the first page
        .pageSize(10)
        .build();

api.listTasks(query);
```

The demo also creates a third task, `Pay rent`, before this query; it is excluded
because the search is for `buy`.

```json
{
  "status": 200,
  "body": [
    {
      "completed": false,
      "createdAt": 1795751506063,
      "description": "Whole wheat bread",
      "dueDate": "2026-12-01",
      "id": 2,
      "parentId": 1,
      "priority": "LOW",
      "title": "Buy bread"
    },
    {
      "completed": false,
      "createdAt": 1795751505911,
      "description": "2L cow milk",
      "dueDate": "2026-12-03",
      "id": 1,
      "parentId": 0,
      "priority": "HIGH",
      "title": "Buy milk"
    }
  ]
}
```

The query also supports `completed(false)` and `priority("HIGH")`; all criteria
are applied in the same call before sorting and pagination.

#### `updateTask`

```java
var update = request("Buy milk", "1L cow milk + 1L buffalo milk", "HIGH", "2026-12-03");
update.setCompleted(true);
api.updateTask(1, update);
```

```json
{
  "status": 200,
  "body": {
    "completed": true,
    "createdAt": 1795751505911,
    "description": "1L cow milk + 1L buffalo milk",
    "dueDate": "2026-12-03",
    "id": 1,
    "parentId": 0,
    "priority": "HIGH",
    "title": "Buy milk"
  }
}
```

#### `deleteTask`

```java
api.deleteTask(1);
```

```json
{"status": 200, "body": true}
```

This also deletes task `2` (the child created by `createSubtask`) and the
breakdown children created in this example session.

#### `createTaskFromText` (AI task creation)

```java
api.createTaskFromText("Buy milk from the corner store", "ai-create-1");
```

```json
{
  "status": 201,
  "body": {
    "completed": false,
    "createdAt": 1795751506163,
    "description": "2L cow milk",
    "dueDate": "2026-12-01",
    "id": 4,
    "parentId": 0,
    "priority": "MEDIUM",
    "title": "Buy milk"
  }
}
```

The example uses a fake LLM that returns:

```json
{"title":"Buy milk","description":"2L cow milk","dueDate":"2026-12-01","recurrence":null}
```

With the real provider, the parsed task depends on the model response. A retry
with the same key and same text returns the stored response and does not call the
model again.

#### `breakDownTask` (AI breakdown)

```java
api.breakDownTask(1, "breakdown-1");
```

```json
{
  "status": 200,
  "body": [
    {
      "completed": false,
      "createdAt": 1795751506168,
      "description": "2L cow milk",
      "dueDate": "2026-12-03",
      "id": 5,
      "parentId": 1,
      "priority": "HIGH",
      "title": "Buy milk"
    },
    {
      "completed": false,
      "createdAt": 1795751506168,
      "description": "Whole wheat bread",
      "dueDate": "2026-12-03",
      "id": 6,
      "parentId": 1,
      "priority": "HIGH",
      "title": "Buy bread"
    }
  ]
}
```

The fake model reply in the demo is:

```text
Buy milk|2L cow milk
Buy bread|Whole wheat bread
```

If any line is invalid, or the model call fails, the API returns `502` and saves
no subtasks. Example failure shape:

```json
{
  "status": 502,
  "body": {
    "code": "BAD_GATEWAY",
    "message": "LLM returned invalid subtasks",
    "details": [
      {"field": "subtasks", "issue": "Invalid subtask line: This line has no separator"}
    ]
  }
}
```

#### Validation error

```java
api.createTask(request(null, "Description", "HIGH", "2026-12-01"), null);
```

```json
{
  "status": 400,
  "body": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [
      {"field": "title", "issue": "Title cannot be null or empty"}
    ]
  }
}
```

#### Not found

```java
api.getTask(1); // after task 1 has been deleted
```

```json
{
  "status": 404,
  "body": {
    "code": "NOT_FOUND",
    "message": "Task with Id 1 not found",
    "details": null
  }
}
```

#### Idempotency conflict

```java
api.createTask(request("Idempotent task", "Original", "HIGH", "2026-12-01"), "same-key");
api.createTask(request("Different task", "Changed", "HIGH", "2026-12-02"), "same-key");
```

The first call returns `201`. The second call returns:

```json
{
  "status": 422,
  "body": {
    "code": "CONFLICT",
    "message": "Idempotency key 'same-key' was already used with a different request",
    "details": null
  }
}
```

## Design patterns

| Pattern | Where | Purpose |
|---|---|---|
| **Builder** | `Task.TaskBuilder`, `TaskQuery.Builder` | Construct complex immutable/optional state step by step, especially when most query fields are optional. |
| **Singleton** | `IdempotencyStore` | One process-wide key store with a private constructor and `getInstance()`. Its `execute` method is `synchronized` so concurrent retries cannot both create a task. |
| **Factory** | `ErrorResponseFactory`, `BreakdownRequestFactory` | `ErrorResponseFactory` maps an exception to the correct `ApiResponse`/`ErrorDto`; `BreakdownRequestFactory` creates a validated-shape `TaskRequest` from a parent task and model-provided text. |
| **Strategy** | `BreakdownStrategy` → `RuleBasedBreakdownStrategy`, `LlmBreakdownStrategy`; `TaskFilter` → `CompletedFilter`, `PriorityFilter`, `SearchFilter`; `SortStrategy` → the four created/dueDate sort classes | Swap algorithms without editing the service. `TaskQueryEngine` iterates over registered filters/strategies, so adding a new filter or sort is a new class plus one registry entry, not a change to the pipeline. |
| **Observer** | Not used | The current requirements do not include task lifecycle events or subscribers. If audit logs, notifications, or cache invalidation are added later, `TaskService` is the natural event source and listeners could observe created/updated/deleted events. |

`TaskApi` also acts as a **facade** over `TaskService`, and `TaskFilters` /
`TaskSorts` act as small registries for the strategy implementations.

## The prompts

### Task-creation prompt

Defined in `HttpLlmClient.taskSystemPrompt()`; `{{today}}` is replaced with the
current date before the request is sent:

```text
Today's date is {{today}} (format YYYY-MM-DD).
Turn the user's text into ONE task.
Reply with ONLY a JSON object, no explanation, no code fences, with exactly these fields:
If you cannot determine the dueDate from the text then set it as today's date.
  "title": short string, required
  "description": string, required
  "dueDate": YYYY-MM-DD or null
  "recurrence": string such as "monthly", or null
```

Why this prompt:

- The date is supplied so relative phrases such as "tomorrow" can become a real
  `YYYY-MM-DD` value.
- The model is told to return exactly one JSON object with named fields, which
  matches `ParsedTask` and keeps parsing deterministic.
- "No explanation, no code fences" avoids markdown that would break JSON
  deserialization.
- `temperature=0.0`, JSON response mode (where the provider supports it), and
  disabled thinking reduce variation.
- The prompt explicitly allows `dueDate: null`, but the service validates the
  final task and returns `400` if the parsed task is incomplete.

### Breakdown prompt

Defined in `LlmBreakdownStrategy.buildPrompt(Task)`:

```text
Break the following task into smaller subtasks.
Reply with ONLY one subtask per line in this exact format:
title|description
Do not add numbering, bullets, headings, markdown, or code fences.
Return at least two subtasks.

Task title: <title>
Task description: <description>
```

Why this prompt:

- A line-oriented `title|description` format is trivial to parse with plain
  `String` operations (`indexOf('|')`, `substring`, `trim`) and avoids a second
  JSON schema.
- Explicitly banning numbering, bullets, headings, markdown, and code fences
  reduces the number of malformed replies. The parser still tolerates simple
  bullets and code fences as a safety net.
- Passing both title and description gives the model enough context to produce
  useful subtasks.
- Asking for at least two subtasks matches the intent of a breakdown.
- Every returned line is validated before anything is saved. One bad line makes
  the whole operation return `502`, preventing partially-created breakdowns.
- `RuleBasedBreakdownStrategy` remains available for offline work and tests; it
  splits the title (or description) on `and` or commas.

## What the AI-generated tests missed and what changed

The first AI-generated test pass focused on the happy path: create, get, list,
basic filters, and a single idempotency retry. Reviewing it exposed gaps and a
few production bugs:

- **Missing validation coverage.** Added tests for invalid priority, `sortBy`,
  `order`, `page`, and `pageSize`, and confirmed each returns `400`.
- **Sorting ties and null due dates.** Created-at values can be identical in a
  tight loop, and due dates can be null. Added task-id tie-breakers and kept
  null due dates at the end in both directions.
- **Concurrency.** Added a same-key concurrent creation test; `IdempotencyStore`
  is synchronized so exactly one task is created.
- **Idempotency failure semantics.** Added a test proving a failed validation
  does not consume the key, so the client can fix the request and retry.
- **Partial breakdown saves.** Added tests for an invalid LLM line and an LLM
  call failure, proving no subtasks are saved and the response is `502`.
- **Sloppy model formatting.** Added normalization for code fences, bullets, and
  simple numbering, plus a test for that output.
- **Cascade delete and subtask updates.** Added tests for deleting a parent and
  for rejecting a subtask as a parent.

Changes made because of those findings:

- `TaskApi` now uses the injected `TaskService` instead of silently constructing
  another one.
- `TaskApi.createTaskFromText` and `breakDownTask` catch `ApiException`, so
  `BadGatewayException` becomes `502` instead of the generic `500`.
- `TaskRequest.parentTaskId` defaults to `Optional.empty()`, removing a null
  `Optional` NPE during normal creation.
- `createTask` persists the validated priority and due date instead of forcing
  `MEDIUM` and dropping the date.
- `updateTask` preserves `parentId` and `createdAt`, so updating a subtask does
  not move it to the top level or reset its created time.
- `ErrorResponseFactory` includes `BadGatewayException` issues in the error
  details.
- The query engine validates sorting/pagination and returns `400` for bad input.
- The breakdown parser tolerates harmless model formatting while still rejecting
  genuinely invalid items before saving anything.
