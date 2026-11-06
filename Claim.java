package kr.landclaim;

import java.util.UUID;

/** 청크 하나의 소유 정보. */
public final class Claim {
    public enum Type { PLAYER, PUBLIC }

    public final int cx;
    public final int cz;
    public final Type type;
    public final UUID owner;      // 공용 땅이면 null
    public String ownerName;      // 표시용 이름 (UUID가 진짜 소유 기준)

    public Claim(int cx, int cz, Type type, UUID owner, String ownerName) {
        this.cx = cx;
        this.cz = cz;
        this.type = type;
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public boolean isPublic() {
        return type == Type.PUBLIC;
    }

    public String ownerKey() {
        return isPublic() ? "public" : owner.toString();
    }
}
