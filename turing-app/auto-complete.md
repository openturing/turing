Here is a simple flow chart:

```mermaid
sequenceDiagram
    participant N0
    participant N1
    participant N2
    participant N3
    participant Accept
    participant Reject
    N0->>N1: Word
    N0->>Reject: StopWord
    N0->>Reject: Empty
    N0->>N2: Special Stop Word
    
    N1->>Reject: Word
    N1->>Reject: StopWord
    N1->>Accept: Empty
    N1->>N2: Special Stop Word

    N2->>N3: Word
    N2->>N2: StopWord
    N2->>Reject: Empty
    N2->>N2: Special Stop Word

    N3->>Reject: Word
    N3->>Reject: StopWord
    N3->>Accept: Empty
    N3->>Reject: Special Stop Word
    
```

```mermaid
sequenceDiagram
    participant FirstTermOrStopWord
    participant PreviousTermIsWord
    participant Accept
    participant Reject

    FirstTermOrStopWord->>PreviousTermIsWord: Word
    FirstTermOrStopWord->>FirstTermOrStopWord: StopWord
    FirstTermOrStopWord->>Reject: Empty

    PreviousTermIsWord->>Reject: Word
    PreviousTermIsWord->>Reject: StopWord
    PreviousTermIsWord->>Accept: Empty
```
