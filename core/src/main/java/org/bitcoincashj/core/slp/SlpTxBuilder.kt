package org.bitcoincashj.core.slp

import io.reactivex.Single
import org.bitcoincashj.core.*
import org.bitcoincashj.kits.SlpAppKit
import org.bitcoincashj.kits.SlpBIP47AppKit
import org.bitcoincashj.protocols.payments.slp.SlpPaymentSession
import org.bitcoincashj.script.Script
import org.bitcoincashj.script.ScriptBuilder
import org.bitcoincashj.script.ScriptChunk
import org.bitcoincashj.script.ScriptOpCodes
import org.bitcoincashj.wallet.SendRequest
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.util.encoders.Hex
import java.math.BigDecimal
import java.nio.ByteBuffer

class SlpTxBuilder {
    companion object {
        @JvmStatic
        fun buildTx(tokenId: String, amount: Double, toAddress: String, slpAppKit: SlpAppKit, aesKey: KeyParameter?, allowUnconfirmed: Boolean): Single<Transaction> {
            return sendTokenUtxoSelection(tokenId, amount, slpAppKit)
                    .map {
                        val addrTo = SlpAddressFactory.create().getFromFormattedAddress(slpAppKit.wallet().params, toAddress).toCash()
                        // Add OP RETURN and receiver output
                        val req = SendRequest.createSlpTransaction(slpAppKit.wallet().params)

                        if (allowUnconfirmed)
                            req.allowUnconfirmed()

                        req.aesKey = aesKey
                        req.shuffleOutputs = false
                        req.utxos = null
                        req.feePerKb = Coin.valueOf(1000L)

                        val opReturn = if (it.quantities.size == 1) {
                            SlpOpReturnOutputSend(it.tokenId, it.quantities[0].toLong(), 0)
                        } else {
                            SlpOpReturnOutputSend(it.tokenId, it.quantities[0].toLong(), it.quantities[1].toLong())
                        }

                        req.tx.addOutput(Coin.ZERO, opReturn.script)
                        req.tx.addOutput(slpAppKit.wallet().params.minNonDustOutput, addrTo)

                        // Send our token change back to our SLP address
                        if (it.quantities.size == 2) {
                            req.tx.addOutput(slpAppKit.wallet().params.minNonDustOutput, CashAddressFactory.create().getFromFormattedAddress(slpAppKit.wallet().params, slpAppKit.freshSlpChangeAddress().toCash().toString()))
                        }

                        // Send our BCH change back to our BCH address
                        if (it.changeSatoshi >= DUST_LIMIT) {
                            req.tx.addOutput(Coin.valueOf(it.changeSatoshi), slpAppKit.wallet().freshChangeAddress())
                        }

                        it.selectedUtxos.forEach { req.tx.addInput(it) }
                        slpAppKit.wallet().signTransaction(req)
                        slpAppKit.wallet().commitTx(req.tx)
                        val tx = req.tx
                        tx
                    }
        }

        @JvmStatic
        fun buildTx(tokenId: String, amount: Double, toAddress: String, slpAppKit: SlpBIP47AppKit, aesKey: KeyParameter?, allowUnconfirmed: Boolean): Single<Transaction> {
            return sendTokenUtxoSelection(tokenId, amount, slpAppKit)
                    .map {
                        val addrTo = SlpAddressFactory.create().getFromFormattedAddress(slpAppKit.wallet().params, toAddress).toCash()
                        // Add OP RETURN and receiver output
                        val req = SendRequest.createSlpTransaction(slpAppKit.wallet().params)

                        if (allowUnconfirmed)
                            req.allowUnconfirmed()

                        req.aesKey = aesKey
                        req.shuffleOutputs = false
                        req.utxos = null
                        req.feePerKb = Coin.valueOf(1000L)

                        val opReturn = if (it.quantities.size == 1) {
                            SlpOpReturnOutputSend(it.tokenId, it.quantities[0].toLong(), 0)
                        } else {
                            SlpOpReturnOutputSend(it.tokenId, it.quantities[0].toLong(), it.quantities[1].toLong())
                        }

                        req.tx.addOutput(Coin.ZERO, opReturn.script)
                        req.tx.addOutput(slpAppKit.wallet().params.minNonDustOutput, addrTo)

                        // Send our token change back to our SLP address
                        if (it.quantities.size == 2) {
                            req.tx.addOutput(slpAppKit.wallet().params.minNonDustOutput, slpAppKit.freshSlpChangeAddress().toCash())
                        }

                        // Send our BCH change back to our BCH address
                        if (it.changeSatoshi >= DUST_LIMIT) {
                            req.tx.addOutput(Coin.valueOf(it.changeSatoshi), slpAppKit.wallet().freshChangeAddress())
                        }

                        it.selectedUtxos.forEach { req.tx.addInput(it) }
                        slpAppKit.wallet().signTransaction(req)
                        slpAppKit.wallet().commitTx(req.tx)
                        val tx = req.tx
                        tx
                    }
        }

        @JvmStatic
        fun buildTxBip70(tokenId: String, slpAppKit: SlpAppKit, aesKey: KeyParameter?, rawTokens: List<Long>, addresses: List<String>, paymentSession: SlpPaymentSession, allowUnconfirmed: Boolean): Single<Transaction> {
            return sendTokenUtxoSelectionBip70(tokenId, rawTokens, slpAppKit)
                    .map {
                        // Add OP RETURN and receiver output
                        val req = SendRequest.createSlpTransaction(slpAppKit.wallet().params)

                        if (allowUnconfirmed)
                            req.allowUnconfirmed()

                        req.aesKey = aesKey
                        req.shuffleOutputs = false
                        req.utxos = null
                        req.feePerKb = Coin.valueOf(1000L)

                        var opReturnScript: Script = paymentSession.slpOpReturn
                        val lokad = byteArrayOf(83, 76, 80, 0)
                        val type = byteArrayOf(1)
                        val PUSHDATA_BYTES = 8
                        var scriptBuilder = ScriptBuilder()
                                .op(ScriptOpCodes.OP_RETURN)
                                .data(lokad)
                                .addChunk(ScriptChunk(type.size, type))
                                .data("SEND".toByteArray())
                                .data(Hex.decode(tokenId))
                        for (x in rawTokens.indices) {
                            scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(rawTokens[x]).array())
                        }

                        if (it.quantities.size == 2) {
                            scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(it.quantities[1].toLong()).array())
                        }

                        opReturnScript = scriptBuilder.build()

                        req.tx.addOutput(Coin.ZERO, opReturnScript)

                        for (x in addresses.indices) {
                            val addrTo = SlpAddressFactory.create().getFromFormattedAddress(slpAppKit.wallet().params, addresses[x]).toCash()
                            req.tx.addOutput(slpAppKit.wallet().params.minNonDustOutput, addrTo)
                        }

                        // Send our token change back to our SLP address
                        if (it.quantities.size == 2) {
                            req.tx.addOutput(slpAppKit.wallet().params.minNonDustOutput, slpAppKit.currentSlpChangeAddress().toCash())
                        }

                        // Send our BCH change back to our BCH address
                        if (it.changeSatoshi >= DUST_LIMIT) {
                            req.tx.addOutput(Coin.valueOf(it.changeSatoshi), slpAppKit.wallet().freshChangeAddress())
                        }

                        it.selectedUtxos.forEach { req.tx.addInput(it) }
                        slpAppKit.wallet().signTransaction(req)
                        slpAppKit.wallet().commitTx(req.tx)
                        val tx = req.tx
                        tx
                    }
        }

        @JvmStatic
        fun buildTxBip70(tokenId: String, slpAppKit: SlpBIP47AppKit, aesKey: KeyParameter?, rawTokens: List<Long>, addresses: List<String>, paymentSession: SlpPaymentSession, allowUnconfirmed: Boolean): Single<Transaction> {
            return sendTokenUtxoSelectionBip70(tokenId, rawTokens, slpAppKit)
                    .map {
                        // Add OP RETURN and receiver output
                        val req = SendRequest.createSlpTransaction(slpAppKit.wallet().params)

                        if (allowUnconfirmed)
                            req.allowUnconfirmed()

                        req.aesKey = aesKey
                        req.shuffleOutputs = false
                        req.utxos = null
                        req.feePerKb = Coin.valueOf(1000L)

                        var opReturnScript: Script = paymentSession.slpOpReturn
                        val lokad = byteArrayOf(83, 76, 80, 0)
                        val type = byteArrayOf(1)
                        val PUSHDATA_BYTES = 8
                        var scriptBuilder = ScriptBuilder()
                                .op(ScriptOpCodes.OP_RETURN)
                                .data(lokad)
                                .addChunk(ScriptChunk(type.size, type))
                                .data("SEND".toByteArray())
                                .data(Hex.decode(tokenId))
                        for (x in rawTokens.indices) {
                            scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(rawTokens[x]).array())
                        }

                        if (it.quantities.size == 2) {
                            scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(it.quantities[1].toLong()).array())
                        }

                        opReturnScript = scriptBuilder.build()

                        req.tx.addOutput(Coin.ZERO, opReturnScript)

                        for (x in addresses.indices) {
                            val addrTo = SlpAddressFactory.create().getFromFormattedAddress(slpAppKit.params(), addresses[x]).toCash()
                            req.tx.addOutput(slpAppKit.params().minNonDustOutput, addrTo)
                        }

                        // Send our token change back to our SLP address
                        if (it.quantities.size == 2) {
                            req.tx.addOutput(slpAppKit.params().minNonDustOutput, slpAppKit.currentSlpChangeAddress().toCash())
                        }

                        // Send our BCH change back to our BCH address
                        if (it.changeSatoshi >= DUST_LIMIT) {
                            req.tx.addOutput(Coin.valueOf(it.changeSatoshi), slpAppKit.wallet().freshChangeAddress())
                        }

                        it.selectedUtxos.forEach { req.tx.addInput(it) }
                        slpAppKit.wallet().signTransaction(req)
                        slpAppKit.wallet().commitTx(req.tx)
                        val tx = req.tx
                        tx
                    }
        }

        private val OP_RETURN_NUM_BYTES_BASE: Int = 55
        private val QUANTITY_NUM_BYTES: Int = 9
        val maxRawAmount = BigDecimal(ULong.MAX_VALUE.toString())
        const val DUST_LIMIT: Long = 546

        fun sendTokenUtxoSelection(tokenId: String, numTokens: Double, slpAppKit: SlpAppKit): Single<SendTokenUtxoSelection> {
            return Single.fromCallable {
                // Wrap for now to protect against blocking non reactive calls
                val tokenDetails: SlpToken = slpAppKit.getSlpToken(tokenId)
                val sendTokensRaw = toRawAmount(numTokens.toBigDecimal(), tokenDetails)
                var sendSatoshi = DUST_LIMIT // At least one dust limit output to the token receiver

                val utxos = slpAppKit.wallet().utxos

                // First select enough token utxo's and just take what we get in terms of BCH
                var inputTokensRaw = ULong.MIN_VALUE
                var inputSatoshi = 0L
                val selectedUtxos = slpAppKit.slpUtxos
                        .filter { it.tokenId == tokenId }
                        .sortedBy { it.tokenAmountRaw }
                        .takeWhile {
                            val amountTooLow = inputTokensRaw < sendTokensRaw
                            if (amountTooLow) {
                                inputTokensRaw += it.tokenAmountRaw.toULong()
                                inputSatoshi += (it.txUtxo.value.value - 148) // Deduct input fee
                            }
                            amountTooLow
                        }
                        .map { it.txUtxo }
                        .toMutableList()
                if (inputTokensRaw < sendTokensRaw) {
                    throw RuntimeException("insufficient token balance=$inputTokensRaw")
                } else if (inputTokensRaw > sendTokensRaw) {
                    // If there's token change we need at least another dust limit worth of BCH
                    sendSatoshi += DUST_LIMIT
                }

                val propagationExtraFee = 50 // When too close 1sat/byte tx's don't propagate well
                val numOutputs = 3 // Assume three outputs in addition to the op return.
                val numQuanitites = 2 // Assume one token receiver and the token receiver
                val fee = outputFee(numOutputs) + sizeInBytes(numQuanitites) + propagationExtraFee

                // If we can not yet afford the fee + dust limit to send, use pure BCH utxo's
                selectedUtxos.addAll(utxos
                        .sortedBy { it.value.value }
                        .takeWhile {
                            val amountTooLow = inputSatoshi <= (sendSatoshi + fee)
                            if (amountTooLow) {
                                inputSatoshi += (it.value.value - 148) // Deduct input fee
                            }
                            amountTooLow
                        })

                val changeSatoshi = inputSatoshi - sendSatoshi - fee
                if (changeSatoshi < 0) {
                    throw IllegalArgumentException("Insufficient BCH balance=$inputSatoshi required $sendSatoshi + fees")
                }

                // We have enough tokens and BCH. Create the transaction
                val quantities = mutableListOf(sendTokensRaw)
                val changeTokens = inputTokensRaw - sendTokensRaw
                if (changeTokens > 0u) {
                    quantities.add(changeTokens)
                }

                SendTokenUtxoSelection(tokenId, quantities, changeSatoshi, selectedUtxos)
            }
        }

        fun sendTokenUtxoSelection(tokenId: String, numTokens: Double, slpAppKit: SlpBIP47AppKit): Single<SendTokenUtxoSelection> {
            return Single.fromCallable {
                // Wrap for now to protect against blocking non reactive calls
                val tokenDetails: SlpToken = slpAppKit.getSlpToken(tokenId)
                val sendTokensRaw = toRawAmount(numTokens.toBigDecimal(), tokenDetails)
                var sendSatoshi = DUST_LIMIT // At least one dust limit output to the token receiver

                val utxos = slpAppKit.wallet().utxos

                // First select enough token utxo's and just take what we get in terms of BCH
                var inputTokensRaw = ULong.MIN_VALUE
                var inputSatoshi = 0L
                val selectedUtxos = slpAppKit.slpUtxos
                        .filter { it.tokenId == tokenId }
                        .sortedBy { it.tokenAmountRaw }
                        .takeWhile {
                            val amountTooLow = inputTokensRaw < sendTokensRaw
                            if (amountTooLow) {
                                inputTokensRaw += it.tokenAmountRaw.toULong()
                                inputSatoshi += (it.txUtxo.value.value - 148) // Deduct input fee
                            }
                            amountTooLow
                        }
                        .map { it.txUtxo }
                        .toMutableList()
                if (inputTokensRaw < sendTokensRaw) {
                    throw RuntimeException("insufficient token balance=$inputTokensRaw")
                } else if (inputTokensRaw > sendTokensRaw) {
                    // If there's token change we need at least another dust limit worth of BCH
                    sendSatoshi += DUST_LIMIT
                }

                val propagationExtraFee = 50 // When too close 1sat/byte tx's don't propagate well
                val numOutputs = 3 // Assume three outputs in addition to the op return.
                val numQuanitites = 2 // Assume one token receiver and the token receiver
                val fee = outputFee(numOutputs) + sizeInBytes(numQuanitites) + propagationExtraFee

                // If we can not yet afford the fee + dust limit to send, use pure BCH utxo's
                selectedUtxos.addAll(utxos
                        .sortedBy { it.value.value }
                        .takeWhile {
                            val amountTooLow = inputSatoshi <= (sendSatoshi + fee)
                            if (amountTooLow) {
                                inputSatoshi += (it.value.value - 148) // Deduct input fee
                            }
                            amountTooLow
                        })

                val changeSatoshi = inputSatoshi - sendSatoshi - fee
                if (changeSatoshi < 0) {
                    throw IllegalArgumentException("Insufficient BCH balance=$inputSatoshi required $sendSatoshi + fees")
                }

                // We have enough tokens and BCH. Create the transaction
                val quantities = mutableListOf(sendTokensRaw)
                val changeTokens = inputTokensRaw - sendTokensRaw
                if (changeTokens > 0u) {
                    quantities.add(changeTokens)
                }

                SendTokenUtxoSelection(tokenId, quantities, changeSatoshi, selectedUtxos)
            }
        }

        fun sendTokenUtxoSelectionBip70(tokenId: String, tokensRaw: List<Long>, slpAppKit: SlpAppKit): Single<SendTokenUtxoSelection> {
            return Single.fromCallable {
                // Wrap for now to protect against blocking non reactive calls
                val tokenDetails: SlpToken = slpAppKit.getSlpToken(tokenId)
                var numTokens: Long = 0
                var sendSatoshi: Long = 0
                for (x in tokensRaw.indices) {
                    numTokens += tokensRaw[x]
                    sendSatoshi += DUST_LIMIT

                }
                val sendTokensRaw = numTokens.toULong()
                val utxos = slpAppKit.wallet().utxos

                // First select enough token utxo's and just take what we get in terms of BCH
                var inputTokensRaw = ULong.MIN_VALUE
                var inputSatoshi = 0L
                val selectedUtxos = slpAppKit.slpUtxos
                        .filter { it.tokenId == tokenId }
                        .sortedBy { it.tokenAmountRaw }
                        .takeWhile {
                            val amountTooLow = inputTokensRaw < sendTokensRaw
                            if (amountTooLow) {
                                inputTokensRaw += it.tokenAmountRaw.toULong()
                                inputSatoshi += (it.txUtxo.value.value - 148) // Deduct input fee
                            }
                            amountTooLow
                        }
                        .map { it.txUtxo }
                        .toMutableList()
                if (inputTokensRaw < sendTokensRaw) {
                    throw RuntimeException("insufficient token balance=$inputTokensRaw")
                } else if (inputTokensRaw > sendTokensRaw) {
                    // If there's token change we need at least another dust limit worth of BCH
                    sendSatoshi += DUST_LIMIT
                }

                val propagationExtraFee = (tokensRaw.size + 1) * 50 // When too close 1sat/byte tx's don't propagate well
                val numOutputs = tokensRaw.size + 1 // Assume outputs = tokens raw array + change, in addition to the OP_RETURN
                val numQuanitites = tokensRaw.size // Assume tokens amount = tokens raw array
                val fee = outputFee(numOutputs) + sizeInBytes(numQuanitites) + propagationExtraFee

                // If we can not yet afford the fee + dust limit to send, use pure BCH utxo's
                selectedUtxos.addAll(utxos
                        .sortedBy { it.value.value }
                        .takeWhile {
                            val amountTooLow = inputSatoshi <= (sendSatoshi + fee)
                            if (amountTooLow) {
                                inputSatoshi += (it.value.value - 148) // Deduct input fee
                            }
                            amountTooLow
                        })

                val changeSatoshi = inputSatoshi - sendSatoshi - fee
                if (changeSatoshi < 0) {
                    throw IllegalArgumentException("Insufficient BCH balance=$inputSatoshi required $sendSatoshi + fees")
                }

                // We have enough tokens and BCH. Create the transaction
                val quantities = mutableListOf(sendTokensRaw)
                val changeTokens = inputTokensRaw - sendTokensRaw
                if (changeTokens > 0u) {
                    quantities.add(changeTokens)
                }

                SendTokenUtxoSelection(tokenId, quantities, changeSatoshi, selectedUtxos)
            }
        }

        fun sendTokenUtxoSelectionBip70(tokenId: String, tokensRaw: List<Long>, slpAppKit: SlpBIP47AppKit): Single<SendTokenUtxoSelection> {
            return Single.fromCallable {
                // Wrap for now to protect against blocking non reactive calls
                val tokenDetails: SlpToken = slpAppKit.getSlpToken(tokenId)
                var numTokens: Long = 0
                var sendSatoshi: Long = 0
                for (x in tokensRaw.indices) {
                    numTokens += tokensRaw[x]
                    sendSatoshi += DUST_LIMIT

                }
                val sendTokensRaw = numTokens.toULong()
                val utxos = slpAppKit.wallet().utxos

                // First select enough token utxo's and just take what we get in terms of BCH
                var inputTokensRaw = ULong.MIN_VALUE
                var inputSatoshi = 0L
                val selectedUtxos = slpAppKit.slpUtxos
                        .filter { it.tokenId == tokenId }
                        .sortedBy { it.tokenAmountRaw }
                        .takeWhile {
                            val amountTooLow = inputTokensRaw < sendTokensRaw
                            if (amountTooLow) {
                                inputTokensRaw += it.tokenAmountRaw.toULong()
                                inputSatoshi += (it.txUtxo.value.value - 148) // Deduct input fee
                            }
                            amountTooLow
                        }
                        .map { it.txUtxo }
                        .toMutableList()
                if (inputTokensRaw < sendTokensRaw) {
                    throw RuntimeException("insufficient token balance=$inputTokensRaw")
                } else if (inputTokensRaw > sendTokensRaw) {
                    // If there's token change we need at least another dust limit worth of BCH
                    sendSatoshi += DUST_LIMIT
                }

                val propagationExtraFee = (tokensRaw.size + 1) * 50 // When too close 1sat/byte tx's don't propagate well
                val numOutputs = tokensRaw.size + 1 // Assume outputs = tokens raw array + change, in addition to the OP_RETURN
                val numQuanitites = tokensRaw.size // Assume tokens amount = tokens raw array
                val fee = outputFee(numOutputs) + sizeInBytes(numQuanitites) + propagationExtraFee

                // If we can not yet afford the fee + dust limit to send, use pure BCH utxo's
                selectedUtxos.addAll(utxos
                        .sortedBy { it.value.value }
                        .takeWhile {
                            val amountTooLow = inputSatoshi <= (sendSatoshi + fee)
                            if (amountTooLow) {
                                inputSatoshi += (it.value.value - 148) // Deduct input fee
                            }
                            amountTooLow
                        })

                val changeSatoshi = inputSatoshi - sendSatoshi - fee
                if (changeSatoshi < 0) {
                    throw IllegalArgumentException("Insufficient BCH balance=$inputSatoshi required $sendSatoshi + fees")
                }

                // We have enough tokens and BCH. Create the transaction
                val quantities = mutableListOf(sendTokensRaw)
                val changeTokens = inputTokensRaw - sendTokensRaw
                if (changeTokens > 0u) {
                    quantities.add(changeTokens)
                }

                SendTokenUtxoSelection(tokenId, quantities, changeSatoshi, selectedUtxos)
            }
        }

        fun toRawAmount(amount: BigDecimal, slpToken: SlpToken): ULong {
            var amt = amount
            if (amt > maxRawAmount) {
                throw IllegalArgumentException("amount larger than 8 unsigned bytes")
            } else if (slpToken.decimals == 0) {
                amt = amount.toInt().toBigDecimal()
            } else if (amt.scale() - 1 > slpToken.decimals) {
                throw IllegalArgumentException("${slpToken.ticker} supports maximum ${slpToken.decimals} decimals but amount is $amount")
            }
            return amt.scaleByPowerOfTen(slpToken.decimals).toLong().toULong()
        }

        fun outputFee(numOutputs: Int): Long {
            return numOutputs.toLong() * 34
        }

        fun sizeInBytes(numQuantities: Int): Int {
            return OP_RETURN_NUM_BYTES_BASE + numQuantities * QUANTITY_NUM_BYTES
        }

        data class SendTokenUtxoSelection(
                val tokenId: String, val quantities: List<ULong>, val changeSatoshi: Long,
                val selectedUtxos: List<TransactionOutput>
        )
    }
}
