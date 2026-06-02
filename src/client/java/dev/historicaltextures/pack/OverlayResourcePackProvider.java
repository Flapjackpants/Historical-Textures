package dev.historicaltextures.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class OverlayResourcePackProvider implements RepositorySource {
	public static final String PACK_ID = "historical_textures_overlay";
	private static RepositorySource instance;

	public static void register() {
		instance = new OverlayResourcePackProvider();
	}

	public static RepositorySource getInstance() {
		return instance;
	}

	@Override
	public void loadPacks(Consumer<Pack> consumer) {
		Path overlayRoot = OverlayPackManager.overlayDirectory();
		if (!OverlayPackManager.packExists()) {
			return;
		}

		PackLocationInfo location = new PackLocationInfo(
				PACK_ID,
				Component.translatable("pack.historical_textures.overlay.title"),
				PackSource.BUILT_IN,
				Optional.empty()
		);

		Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
			@Override
			public PackResources openPrimary(PackLocationInfo info) {
				return new PathPackResources(info, overlayRoot);
			}

			@Override
			public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
				return openPrimary(info);
			}
		};

		PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.TOP, false);

		consumer.accept(Pack.readMetaAndCreate(location, supplier, PackType.CLIENT_RESOURCES, selection));
	}
}
