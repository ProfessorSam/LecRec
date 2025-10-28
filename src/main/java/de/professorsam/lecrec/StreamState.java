package de.professorsam.lecrec;

public enum StreamState {
    SEARCH_NEXT_EVENT,
    WAITING_FOR_STREAM,
    RETRYING_LOADING_STREAM,
    RECORDING_STREAM,
    UPLOADING_STREAM
}
