package io.github.kulipai.luahook.core.androlua.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.SingletonImageLoader.get
import io.github.kulipai.luahook.core.androlua.LuaLayout
import io.github.kulipai.luahook.core.androlua.adapter.AsyncLoader.loadImage
import io.github.kulipai.luahook.core.androlua.adapter.LuaRecyclerAdapter.LuaViewHolder
import org.luaj.LuaError
import org.luaj.LuaTable
import org.luaj.lib.jse.CoerceJavaToLua
import java.io.File

class LuaRecyclerAdapter(context: Context, data: LuaTable?, layout: LuaTable) :
    RecyclerView.Adapter<LuaViewHolder?>() {
    private val mContext: Context
    private var mLayout: LuaTable?
    private val mData: LuaTable?
    private val mBaseData: LuaTable?
    private val loadlayout: LuaLayout
    private val imageLoader: ImageLoader
    private var mNotifyOnChange = true

    constructor(context: Context, layout: LuaTable) : this(context, null, layout)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LuaViewHolder {
        try {
            val holder = LuaTable()
            val view = loadlayout.load(mLayout!!, holder)
            val itemView = view.touserdata(View::class.java) ?: throw LuaError("loadlayout did not return a View")
            return LuaViewHolder(itemView, holder)
        } catch (e: LuaError) {
            return LuaViewHolder(View(mContext), LuaTable())
        }
    }

    interface DataBinder {
        fun bind(binding: LuaTable?, data: LuaTable?, holder: LuaViewHolder?, position: Int)
    }

    private var dataBinder: DataBinder? = null

    init {
        var data = data
        var layout = layout
        mContext = context
        if (data == null) data = LuaTable()
        if (layout.length() == layout.size() && data.length() != data.size()) {
            mLayout = data
            data = layout
            layout = mLayout!!
        }
        mLayout = layout
        mData = data
        mBaseData = data
        val context1 = mContext
        imageLoader = get(context1)
        loadlayout = LuaLayout(context1)
        //loadlayout.load(mLayout, new LuaTable());
    }

    fun setDataBinder(binder: DataBinder?) {
        this.dataBinder = binder
    }

    @SuppressLint("PendingBindings")
    override fun onBindViewHolder(holder: LuaViewHolder, position: Int) {
        if (dataBinder != null) {
            dataBinder!!.bind(
                holder.binding,
                mData!!.get(position + 1).checktable(),
                holder,
                position
            )
        } else {
            // 默认绑定逻辑
            val item = mData!!.get(position + 1)
            if (item.istable()) {
                val itemTable = item.checktable()
                val keys = itemTable.keys()
                for (key in keys) {
                    val field = key.tojstring()
                    val value = itemTable.jget(field)
                    val view = holder.binding.get(field)
                    if (view.isuserdata(View::class.java)) {
                        setHelper(view.touserdata<View?>(View::class.java), value)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return mData!!.length()
    }

    fun setNotifyOnChange(notifyOnChange: Boolean) {
        mNotifyOnChange = notifyOnChange
    }

    fun add(item: LuaTable?) {
        mBaseData!!.insert(mBaseData.length() + 1, item)
        if (mNotifyOnChange) notifyItemInserted(mData!!.length())
    }

    @Throws(Exception::class)
    fun addAll(items: LuaTable) {
        val len = items.length()
        for (i in 1..len) mBaseData!!.insert(mBaseData.length() + 1, items.get(i))
        if (mNotifyOnChange) notifyItemRangeInserted(0, len)
    }

    @Throws(Exception::class)
    fun insert(position: Int, item: LuaTable?) {
        mBaseData!!.insert(position + 1, item)
        if (mNotifyOnChange) notifyItemInserted(position)
    }

    @Throws(Exception::class)
    fun remove(position: Int) {
        mBaseData!!.remove(position + 1)
        if (mNotifyOnChange) notifyItemRemoved(position)
    }

    fun clear() {
        mBaseData!!.clear()
        if (mNotifyOnChange) notifyDataSetChanged()
    }

    //    public void updateData(LuaTable newData) {
    //        LuaTable oldData = this.mData;
    //        this.mData = newData;
    //
    //        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
    //            @Override
    //            public int getOldListSize() {
    //                return oldData.length();
    //            }
    //
    //            @Override
    //            public int getNewListSize() {
    //                return newData.length();
    //            }
    //
    //            @Override
    //            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
    //                // 判断两项是否相同（如通过ID）
    //                return oldData.get(oldItemPosition + 1).raweq(newData.get(newItemPosition + 1));
    //            }
    //
    //            @Override
    //            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
    //                // 判断内容是否完全一致
    //                return oldData.get(oldItemPosition + 1).raweq(newData.get(newItemPosition + 1));
    //            }
    //        });
    //
    //        diffResult.dispatchUpdatesTo(this);
    //    }
    @Throws(LuaError::class)
    private fun setFields(view: View?, fields: LuaTable) {
        val sets = fields.keys()
        for (set in sets) {
            val key2 = set.tojstring()
            val value2 = fields.jget(key2)
            if (key2.equals("src", ignoreCase = true)) setHelper(view, value2)
            else javaSetter(view, key2, value2)
        }
    }

    @Throws(LuaError::class)
    private fun javaSetter(obj: Any?, methodName: String?, value: Any?) {
        CoerceJavaToLua.coerce(obj).jset(methodName, value)
    }

    private fun setHelper(view: View?, value: Any) {
        try {
            // 如果值是参数表
            if (value is LuaTable) {
                setFields(view, value)
            } else if (view is TextView) {
                if (value is CharSequence) view.setText(value)
                else view.setText(value.toString())
            } else if (view is ImageView) {
                if (value is Bitmap) view.setImageBitmap(value)
                else if (value is Drawable) view.setImageDrawable(value)
                else if (value is Number) view.setImageResource(value.toInt())
                else if (value is String || value is Uri || value is File) {
                    loadImage(mContext, imageLoader, value, view)
                    //((ImageView) view).setImageDrawable(new LuaBitmapDrawable(mContext, (String) value));
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
//            mContext.sendError("setHelper", e)
        }
    }

    class LuaViewHolder(itemView: View, var binding: LuaTable) : RecyclerView.ViewHolder(itemView)
}
