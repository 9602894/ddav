// 只展示删除相关修改，其余保持不变
private void deleteSelectedLocal() {
    List<PhotoAdapter.PhotoItem> selected = new ArrayList<>();
    for (PhotoAdapter.PhotoItem item : adapter.getItems()) {
        if (item.isSelected && item.file != null && item.file.exists()) {
            selected.add(item);
        }
    }
    if (selected.isEmpty()) {
        Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
        return;
    }

    new AlertDialog.Builder(this)
            .setTitle("删除本地文件")
            .setMessage("确定要删除选中的 " + selected.size() + " 个文件吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                int success = 0;
                for (PhotoAdapter.PhotoItem item : selected) {
                    if (item.file.delete()) {
                        success++;
                    } else {
                        android.util.Log.e("MainActivity", "删除失败: " + item.file.getAbsolutePath());
                    }
                }
                final int finalSuccess = success;
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "已删除 " + finalSuccess + " 个文件", Toast.LENGTH_SHORT).show();
                    loadLocalPhotos();
                });
            })
            .setNegativeButton("取消", null)
            .show();
}
