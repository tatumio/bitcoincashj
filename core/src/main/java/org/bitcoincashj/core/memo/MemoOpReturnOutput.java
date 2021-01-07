package org.bitcoincashj.core.memo;

import com.google.common.base.Preconditions;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.script.ScriptBuilder;
import org.bitcoincashj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

public class MemoOpReturnOutput {
    private final ScriptBuilder builder;
    public Script getScript() {
        return this.getBuilder().build();
    }
    public ScriptBuilder getBuilder() { return this.builder; }

    private MemoOpReturnOutput(String actionId) {
        this.builder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(Hex.decode(actionId));
    }

    public static class Follow extends MemoOpReturnOutput {
        public Follow(String addressHash160) {
            super("6d06");
            Preconditions.checkArgument(Hex.decode(addressHash160).length <= 20);
            this.getBuilder()
                    .data(Hex.decode(addressHash160));
        }
    }

    public static class Unfollow extends MemoOpReturnOutput {
        public Unfollow(String addressHash160) {
            super("6d07");
            Preconditions.checkArgument(Hex.decode(addressHash160).length <= 20);
            this.getBuilder()
                    .data(Hex.decode(addressHash160));
        }
    }


    public static class Like extends MemoOpReturnOutput {
        public Like(String postTxId) {
            super("6d04");
            Preconditions.checkArgument(Hex.decode(postTxId).length <= 32);
            this.getBuilder()
                    .data(Hex.decode(postTxId));
        }
    }

    public static class PostMemo extends MemoOpReturnOutput {
        public PostMemo(String message) {
            super("6d02");
            Preconditions.checkArgument(message.getBytes().length <= 217);
            this.getBuilder()
                    .data(message.getBytes());
        }
    }

    public static class Reply extends MemoOpReturnOutput {
        public Reply(String postTxId, String message) {
            super("6d03");
            Preconditions.checkArgument(Hex.decode(postTxId).length <= 32);
            Preconditions.checkArgument(message.getBytes().length <= 184);
            this.getBuilder()
                    .data(Hex.decode(postTxId))
                    .data(message.getBytes());
        }
    }

    public static class SetName extends MemoOpReturnOutput {
        public SetName(String name) {
            super("6d01");
            Preconditions.checkArgument(name.getBytes().length <= 217);
            this.getBuilder()
                    .data(name.getBytes());
        }
    }

    public static class SetProfilePicture extends MemoOpReturnOutput {
        public SetProfilePicture(String imgUrl) {
            super("6d0a");
            Preconditions.checkArgument(imgUrl.getBytes().length <= 217);
            this.getBuilder()
                    .data(imgUrl.getBytes());
        }
    }

    public static class SetProfileText extends MemoOpReturnOutput {
        public SetProfileText(String text) {
            super("6d05");
            Preconditions.checkArgument(text.getBytes().length <= 217);
            this.getBuilder()
                    .data(text.getBytes());
        }
    }

    public static class Repost extends MemoOpReturnOutput {
        public Repost(String postTxId, String message) {
            super("6d0b");
            Preconditions.checkArgument(Hex.decode(postTxId).length <= 32);
            Preconditions.checkArgument(message.getBytes().length <= 184);
            this.getBuilder()
                    .data(Hex.decode(postTxId))
                    .data(message.getBytes());
        }
    }
}
