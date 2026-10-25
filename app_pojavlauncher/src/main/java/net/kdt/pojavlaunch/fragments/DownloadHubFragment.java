package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public final class DownloadHubFragment extends Fragment {
    public static final String TAG = "DownloadHubFragment";

    public DownloadHubFragment() {
        super(R.layout.fragment_download_hub);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.download_category_mods).setOnClickListener(v -> {
            Bundle searchArgs = new Bundle(1);
            searchArgs.putBoolean(SearchModFragment.ARG_IS_MODPACK, false);
            Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, searchArgs);
        });
        view.findViewById(R.id.download_category_game_versions).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                        ProfileTypeSelectFragment.TAG, null));

        view.findViewById(R.id.download_category_shaders).setOnClickListener(v ->
                showComingSoon(R.string.download_category_shaders));
        view.findViewById(R.id.download_category_resource_packs).setOnClickListener(v ->
                showComingSoon(R.string.download_category_resource_packs));
        view.findViewById(R.id.download_category_worlds).setOnClickListener(v ->
                showComingSoon(R.string.download_category_worlds));
    }

    private void showComingSoon(int categoryNameResId) {
        Toast.makeText(requireContext(),
                getString(R.string.downloads_category_coming_soon, getString(categoryNameResId)),
                Toast.LENGTH_SHORT).show();
    }
}
