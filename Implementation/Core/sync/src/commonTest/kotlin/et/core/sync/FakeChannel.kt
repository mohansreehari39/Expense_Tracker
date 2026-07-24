package et.core.sync

import kotlinx.coroutines.channels.Channel

/** In-memory [SyncChannel] pair, simulating two directly-connected peers. */
class FakeChannel(
    private val outgoing: Channel<ByteArray>,
    private val incoming: Channel<ByteArray>,
) : SyncChannel {
    override suspend fun send(bytes: ByteArray) = outgoing.send(bytes)
    override suspend fun receive(): ByteArray = incoming.receive()
    override suspend fun close() {
        outgoing.close()
    }

    companion object {
        fun pair(): kotlin.Pair<FakeChannel, FakeChannel> {
            val ab = Channel<ByteArray>(Channel.UNLIMITED)
            val ba = Channel<ByteArray>(Channel.UNLIMITED)
            return FakeChannel(ab, ba) to FakeChannel(ba, ab)
        }
    }
}
