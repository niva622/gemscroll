import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CustomItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.left = space
        outRect.right = space
        outRect.top = space

        // Не добавляем отступ снизу для последнего ряда элементов
        if (parent.getChildAdapterPosition(view) >= state.itemCount - (state.itemCount % 2 ?: 2)) {
            outRect.bottom = 0
        } else {
            outRect.bottom = space
        }
    }
}