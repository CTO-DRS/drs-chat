# detekt

## Metrics

* 81 number of properties

* 96 number of functions

* 20 number of classes

* 1 number of packages

* 15 number of kt files

## Complexity Report

* 1,015 lines of code (loc)

* 834 source lines of code (sloc)

* 653 logical lines of code (lloc)

* 40 comment lines of code (cloc)

* 166 cyclomatic complexity (mcc)

* 45 cognitive complexity

* 2 number of total code smells

* 4% comment source ratio

* 254 mcc per 1,000 lloc

* 3 code smells per 1,000 lloc

## Findings (2)

### style, MaxLineLength (2)

Line detected, which is longer than the defined maximum line length in the code style.

[Documentation](https://detekt.dev/docs/rules/style#maxlinelength)

* /home/z/my-project/drs-chat/android/core/protocol/src/main/kotlin/drs/chat/protocol/EnvelopeCodec.kt:77:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
74                 payloadCiphertext,
75             )
76         val sig = crypto.signEnvelopeHeader(EnvelopeCodec.headerBytesOf(h))
77         return WireEnvelope(h.protocolVersion, h.messageId, h.senderKeyId, h.recipientKeyId, h.createdAt, h.ttlSeconds, h.kind, h.payloadCiphertext, sig)
!! ^ error
78     }
79 }
80 

```

* /home/z/my-project/drs-chat/android/core/protocol/src/main/kotlin/drs/chat/protocol/InnerPayload.kt:42:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
39     }
40 
41     override fun equals(other: Any?): Boolean =
42         this === other || (other is InnerReceipt && ackedMessageId.contentEquals(other.ackedMessageId) && acknowledgedAt == other.acknowledgedAt)
!! ^ error
43 
44     override fun hashCode(): Int {
45         var r = ackedMessageId.contentHashCode()

```

generated with [detekt version 1.23.7](https://detekt.dev/) on 2026-10-15 23:51:25 UTC
