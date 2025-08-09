### 特性
方便自己用
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
`byQuery`的`resultMap`别名变成`wms_material_type$id`和`wms_material_type$wms_material_type$id`
> TODO: 优化别名逻辑，用属性名而不是表名，例如`id`和`parent$id`


> TODO: 优化selectColumns的编排能力，同时尽量避免用户使用直接写sql的情况，比如如果用户改了某个字段名，修改完关联的实体类的属性名就会引发ide对相关引用错误识别


> TODO: fix: 当分页时启用分页优化会将leftJoin里相同的表名去除掉，树形表(parentId)或其余情况分页查询时会造成sql错误
