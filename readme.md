### 特性
方便自己用

### 树形表
```java
@EqualsAndHashCode(callSuper = true)
@Data
@Table("wms_material_type")
public class WmsMaterialType extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 装备类别ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 类别名称 */
    @Excel(name = "类别名称")
    private String name;

    /** 类别编码 */
    @Excel(name = "类别编码")
    private String code;

    /** 类别等级 */
    @Excel(name = "类别等级")
    private String level;

    /** 上级类别ID */
    private Long parentId;

    @RelationManyToOne(selfField = "parentId", targetField = "id")
    private WmsMaterialType parent;

    @Excel(name = "上级类别编号")
    @Column(ignore = true)
    private String parentCode;

    @Excel(name = "上级类别名称")
    @Column(ignore = true)
    private String parentName;

    @Column(ignore = true)
    private String parentSearchValue;

    /** 最小分类标识 */
    @Excel(name = "最小分类标识")
    private String isLeast;

    /** 状态 */
    @Excel(name = "状态")
    private String status;

    @Column(ignore = true)
    private List<WmsMaterialType> children;
}
```

### 修改说明
`byQuery`的`resultMap`中parent引用属性下的别名变成`parent$id`、`parent$name`，`parent`为属性名

更深层次可以变成`A$B$C$user_name`，其中`A$B$C$`为指明的前缀，可以使用提供的asPrefix方法设置

### 使用说明
> `@RelationManyToMany`注解中加入`middleMapping`选项，设置为`true`则会将中间表中的字段映射到目标对象中，如果目标对象中出现该字段但值不为空则不映射。（慎用）
```java
// 使用IService中的默认asPrefix方法 此方法将会使columns都加上前缀和表别名 parent.name -> `parent`.`name` as parent$name
mapper.paginate(page, createDefaultQueryWrapper(wmsMaterialType)
    .select(WMS_MATERIAL_TYPE.DEFAULT_COLUMNS,
            asPrefix(WMS_MATERIAL_TYPE.DEFAULT_COLUMNS, "parent", "parent$")));
```
```java
// 使用IService中的默认asPrefix方法 此方法将会使columns都加上前缀 material.name -> `wms_material`.`name` as material$name
public Page<WmsReceiveLine> selectWmsReceiveLinePage(WmsReceiveLine wmsReceiveLine) {
    return mapper.paginate(PageUtils.getPage(WmsReceiveLine.class), createDefaultQueryWrapper(wmsReceiveLine)
        .select(WMS_RECEIVE_LINE.DEFAULT_COLUMNS,
            asPrefix(WMS_PUT.DEFAULT_COLUMNS, "put$"),
            asPrefix(WMS_MATERIAL.DEFAULT_COLUMNS, "material$"),
            asPrefix(WMS_MATERIAL_ITEM.DEFAULT_COLUMNS, "materialItem$"),
            asPrefix(WMS_MATERIAL_TYPE.DEFAULT_COLUMNS, "materialType$"),
            asPrefix(WMS_SUPPLIER.DEFAULT_COLUMNS, "supplier$"),
            asPrefix(WMS_WAREHOUSE.DEFAULT_COLUMNS, "warehouse$"),
            asPrefix(WMS_AREA.DEFAULT_COLUMNS, "area$"),
            asPrefix(WMS_ROADWAY.DEFAULT_COLUMNS, "roadway$"),
            asPrefix(WMS_SHELF.DEFAULT_COLUMNS, "shelf$")));
}
```

> TODO: 优化selectColumns的编排能力，同时尽量避免用户使用直接写sql的情况，比如如果用户改了某个字段名，修改完关联的实体类的属性名就会引发ide对相关引用错误识别


> TODO: fix: 当分页时启用分页优化会将leftJoin里相同的表名去除掉，树形表(parentId)或其余情况分页查询时会造成sql错误



