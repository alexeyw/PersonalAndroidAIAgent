# External automation contract

Knotwork can be asked to run one of your pipelines by another app on the same
device — a Tasker or MacroDroid profile, a shell script over `adb`, anything that
can send a broadcast. The intent is to **complement** an automation app rather
than replace it: the other app decides *when* something should happen, using its
own permissions and its own condition model, and Knotwork does the language-model
part of *what* happens.

> **Status: contract definition.** This page documents the vocabulary of the
> contract. The receiver that acts on it, the setting that switches it on, the
> guarantees described below, and the worked Tasker / MacroDroid / `adb`
> examples land in later changes; until then nothing here is callable.

## The safety model in one paragraph

The entry point is **off by default**, and even switched on it is **inert until
bound**: you pick exactly one pipeline that outside apps may run. That binding is
an **allowlist, not a default** — a request must name the pipeline it wants, and
a request naming anything else is refused rather than quietly redirected to the
bound one. An external call is not a form of approval: human-in-the-loop
confirmation, per-tool risk overrides, and the destructive-tool block all apply
exactly as they do to the app's own background runs.

## Requesting a run

The caller broadcasts `ACTION_RUN_PIPELINE` with the extras below. The target is
named **either** by id **or** by name, never both; the prompt is supplied
**either** as plain text **or** base64-encoded, never both. Anything ambiguous,
missing, or undecodable is refused with a reason rather than repaired to the
nearest plausible reading.

**An empty value counts as an absent one.** Every key is trimmed, and a key
whose value is blank is treated as if it had not been sent — because the callers
are templates and shell scripts, where an unfilled variable arrives as an empty
string far more often than anyone means to pass one. So an empty `prompt` is
refused as `PROMPT_MISSING` rather than run as an empty message.

**The base64 form uses the standard alphabet** (RFC 4648 §4: `A`–`Z`, `a`–`z`,
`0`–`9`, `+`, `/`). Padding is optional — `aGk=` and `aGk` both decode to `hi`. The
URL-safe alphabet (`-` and `_`) is **not** accepted: the same characters mean
different bytes in the two alphabets, so decoding one as the other would hand the
pipeline text you never wrote. A payload in the wrong alphabet is refused as
`PROMPT_UNDECODABLE`.

Targeting by id is the stable form. A pipeline's **name is targeted exactly**,
so renaming the pipeline breaks every profile that named it — if you expect to
rename it, use the id, which you can copy from the pipeline's entry in the
library.

<!-- AUTO-GEN:CONTRACT_KEYS -->

| Constant | Wire key | Meaning |
| --- | --- | --- |
| `ACTION_RUN_PIPELINE` | `app.knotwork.android.action.RUN_PIPELINE` | Broadcast action a caller sends to ask the app to run a pipeline. |
| `ACTION_RUN_RESULT` | `app.knotwork.android.action.RUN_RESULT` | Default broadcast action of the terminal callback. Used when a caller asks for a callback without naming an action of its own. |
| `EXTRA_PIPELINE_ID` | `pipeline_id` | Request key: id of the pipeline to run. Mutually exclusive with `EXTRA_PIPELINE_NAME`. |
| `EXTRA_PIPELINE_NAME` | `pipeline_name` | Request key: user-visible name of the pipeline to run. Mutually exclusive with `EXTRA_PIPELINE_ID`. |
| `EXTRA_PROMPT` | `prompt` | Request key: the prompt to run the pipeline on, as plain text. Mutually exclusive with `EXTRA_PROMPT_B64`. |
| `EXTRA_PROMPT_B64` | `prompt_b64` | Request key: the prompt as a base64-encoded UTF-8 string, for callers whose shell quoting cannot carry the text intact. Mutually exclusive with `EXTRA_PROMPT`. |
| `EXTRA_REQUEST_ID` | `request_id` | Request key: caller-minted correlation id. Required. The callback carries it back under this same key, so a caller reads back the id it wrote. |
| `EXTRA_RETURN_ACTION` | `return_action` | Request key: broadcast action to send the callback with. Optional; defaults to `ACTION_RUN_RESULT`. |
| `EXTRA_RETURN_PACKAGE` | `return_package` | Request key: package to deliver the callback to as an explicit intent. Optional — omitting it is a valid fire-and-forget call. |
| `EXTRA_STATUS` | `status` | Callback key: the status discriminator (`Accepted` / `Completed` / `Failed` / `Rejected` / `Blocked`). |
| `EXTRA_STATUS_REASON` | `reason` | Callback key: the refusal reason, present only for the `Rejected` and `Blocked` statuses. |

<!-- /AUTO-GEN:CONTRACT_KEYS -->

## Statuses

A request is answered with one of the statuses below. `Rejected` and `Blocked`
carry a reason; the other three do not. The callback is delivered only when the
request asked for one, and it never carries the content of the run — only the
request id (under the same `request_id` key the request used), the status, and
the reason where there is one.

<!-- AUTO-GEN:STATUSES -->

| Status | Meaning |
| --- | --- |
| `Accepted` | The request was admitted and a background run was enqueued. |
| `Completed` | The run started by the request finished successfully. |
| `Failed` | The run started by the request failed, was cancelled, or was interrupted. |
| `Rejected` | The request was refused before anything was started, because of what the request said or how the app is configured. Retrying the identical request against the identical configuration yields the identical refusal. |
| `Blocked` | The request was well-formed and permitted, but a safety ceiling refused it at this moment. Unlike `Rejected` this is a statement about the moment, not about the request: the same request may be admitted later. It is deliberately not a silent enqueue — a caller whose request was dropped on the floor cannot tell that from one that ran. |

<!-- /AUTO-GEN:STATUSES -->

## Refusal reasons

<!-- AUTO-GEN:REJECTION_REASONS -->

| Reason | Meaning |
| --- | --- |
| `CONTRACT_DISABLED` | The external-automation contract is switched off (its default state). |
| `SURFACE_NOT_BOUND` | No pipeline is bound to the external-automation surface (inert until bound). |
| `TARGET_NOT_ALLOWED` | The request named a pipeline other than the one bound to the surface. |
| `TARGET_MISSING` | The request carried no target pipeline at all. |
| `TARGET_AMBIGUOUS` | The request named the target twice, by id and by name, and the two cannot be reconciled. |
| `UNKNOWN_ACTION` | The request carried an action this contract does not define. |
| `PROMPT_MISSING` | The request carried no prompt for the pipeline to run on. |
| `PROMPT_AMBIGUOUS` | The request carried the prompt twice, in plain and base64 form. |
| `PROMPT_UNDECODABLE` | The base64 prompt could not be decoded. |
| `REQUEST_ID_MISSING` | The request carried no request id to correlate its outcome with. |
| `RATE_LIMITED` | Too many external requests were accepted within the rate window. |

<!-- /AUTO-GEN:REJECTION_REASONS -->

## Stability

The action strings, extra keys, status names, and reason names above are frozen
once released. A profile you write against them keeps working; renaming any of
them would break every profile already written, with no way for the author to
see why. The tables on this page are generated from the source declarations and
`./gradlew check` fails if they drift, so what you read here is what the app
actually accepts.
