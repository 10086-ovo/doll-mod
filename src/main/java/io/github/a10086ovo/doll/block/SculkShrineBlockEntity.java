package io.github.a10086ovo.doll.block;

import io.github.a10086ovo.doll.DollMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 幽匿灵龛方块实体。
 * 记录上一次召唤的野生幽匿人偶 UUID，在其死亡之前阻止再次召唤。
 * NBT 持久化，区块重载不丢失状态。
 */
public class SculkShrineBlockEntity extends BlockEntity {

	private static final String NBT_DOLL_UUID = "SummonedDollUuid";

	@Nullable
	private UUID summonedDollUuid = null;

	public SculkShrineBlockEntity(BlockPos pos, BlockState state) {
		super(DollMod.SCULK_SHRINE_BLOCK_ENTITY, pos, state);
	}

	/**
	 * 检查上次召唤的野生幽匿人偶是否还活着。
	 * 如果 UUID 为空或实体已死亡，返回 false（可以再次召唤）。
	 */
	public boolean hasActiveDoll(ServerLevel level) {
		if (summonedDollUuid == null) return false;
		Entity entity = level.getEntity(summonedDollUuid);
		return entity != null && entity.isAlive();
	}

	/** 记录新召唤的野生幽匿人偶 UUID */
	public void setSummonedDoll(UUID uuid) {
		this.summonedDollUuid = uuid;
		setChanged();
	}

	@Override
	public void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (summonedDollUuid != null) {
			output.putString(NBT_DOLL_UUID, summonedDollUuid.toString());
		}
	}

	@Override
	public void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		String uuidStr = input.getString(NBT_DOLL_UUID).orElse(null);
		if (uuidStr != null) {
			try {
				summonedDollUuid = UUID.fromString(uuidStr);
			} catch (IllegalArgumentException e) {
				summonedDollUuid = null;
			}
		}
	}
}