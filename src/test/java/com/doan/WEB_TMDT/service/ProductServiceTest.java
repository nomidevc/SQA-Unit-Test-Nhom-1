package com.doan.WEB_TMDT.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.inventory.entity.InventoryStock;
import com.doan.WEB_TMDT.module.inventory.entity.WarehouseProduct;
import com.doan.WEB_TMDT.module.inventory.repository.InventoryStockRepository;
import com.doan.WEB_TMDT.module.inventory.repository.WarehouseProductRepository;
import com.doan.WEB_TMDT.module.product.dto.CategoryDTO;
import com.doan.WEB_TMDT.module.product.dto.CreateCategoryRequest;
import com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest;
import com.doan.WEB_TMDT.module.product.dto.ProductImageDTO;
import com.doan.WEB_TMDT.module.product.dto.ProductWithSpecsDTO;
import com.doan.WEB_TMDT.module.product.entity.Category;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.entity.ProductImage;
import com.doan.WEB_TMDT.module.product.repository.CategoryRepository;
import com.doan.WEB_TMDT.module.product.repository.ProductImageRepository;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;
import com.doan.WEB_TMDT.module.product.service.CategoryService;
import com.doan.WEB_TMDT.module.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQA UNIT TEST SCRIPT – MODULE: ProductService & CategoryService
 * Nhóm: 1 | Ngày: 2026-04-19 | Công cụ: JUnit 5, SpringBootTest, H2 In-Memory, JaCoCo 0.8.12
 *
 * Total Test Cases: 67
 *   - CategoryService : TC_CAT_01 → TC_CAT_28  (28 cases)
 *   - ProductService  : TC_PROD_01 → TC_PROD_39 (39 cases)
 *
 * Mục đích: Bug Hunting & Quality Assurance – KHÔNG sửa mã nguồn.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ProductServiceTest {

    // ── Services under test ──────────────────────────────────────────────────
    @Autowired private CategoryService categoryService;
    @Autowired private ProductService  productService;

    // ── Repositories (dùng để kiểm tra DB trực tiếp & tạo fixture) ──────────
    @Autowired private CategoryRepository        categoryRepository;
    @Autowired private ProductRepository         productRepository;
    @Autowired private ProductImageRepository    imageRepository;
    @Autowired private WarehouseProductRepository warehouseProductRepository;
    @Autowired private InventoryStockRepository  inventoryStockRepository;

    // ── Shared fixtures ───────────────────────────────────────────────────────
    private Long rootCatId;   // category gốc dùng chung (active=true, parent=null)
    private Long prodId;      // product dùng chung

    @BeforeEach
    public void setUp() {
        // Tạo category gốc chung
        Category root = categoryRepository.save(
            Category.builder().name("Root").slug("root-shared").active(true).displayOrder(10).build()
        );
        rootCatId = root.getId();

        // Tạo product chung
        Product p = productRepository.save(Product.builder()
            .name("Demo Product")
            .sku("SKU-DEMO")
            .price(100.0)
            .stockQuantity(10L)
            .reservedQuantity(2L)
            .category(root)
            .build());
        prodId = p.getId();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ██████  ██████  ██████████  ███████   ██████   ██████  ████████  ██    ██
    // ██      ██    ██    ██      ██         ██       ██    ██ ██     ██  ██  ██
    // ██      ███████    ██      ██████     ██   ███  ██    ██ ████████    ████
    // ██      ██    ██   ██      ██         ██    ██  ██    ██ ██   ██      ██
    //  ██████  ██    ██  ██      ███████     ██████    ██████  ██    ██     ██
    // ═══════════════════════════════════════════════════════════════════════════

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP CAT-1: getAll()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_CAT_01: getAll() – DB có dữ liệu trả về đủ 2 phần tử")
    public void tc_cat_01() {
        categoryRepository.save(Category.builder().name("Cat2").slug("cat2-tc01").active(true).build());
        // setUp đã tạo 1 category, bây giờ thêm 1 nữa → tổng ≥ 2
        List<Category> list = categoryService.getAll();
        assertNotNull(list, "Danh sách không được null");
        assertTrue(list.size() >= 2, "Phải có ít nhất 2 danh mục");
    }

    @Test
    @DisplayName("TC_CAT_02: getAll() – DB rỗng (không có category nào) trả về List rỗng")
    public void tc_cat_02() {
        // Xóa toàn bộ (bao gồm cả setUp fixture)
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        List<Category> list = categoryService.getAll();
        assertNotNull(list, "List không được null dù trống");
        assertTrue(list.isEmpty(), "Phải trả về list rỗng khi DB không có dữ liệu");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP CAT-2: getById()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_CAT_03: getById() – ID hợp lệ → Optional.isPresent() = true, name khớp")
    public void tc_cat_03() {
        Optional<Category> result = categoryService.getById(rootCatId);
        assertTrue(result.isPresent(), "Phải tìm thấy category");
        assertEquals("Root", result.get().getName());
    }

    @Test
    @DisplayName("TC_CAT_04: getById() – ID không tồn tại → Optional.isEmpty() = true")
    public void tc_cat_04() {
        Optional<Category> result = categoryService.getById(99999L);
        assertTrue(result.isEmpty(), "Phải trả về Optional.empty() cho ID không tồn tại");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP CAT-3: create() / update() / delete()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_CAT_05: create() – Tạo category hợp lệ → ID != null")
    public void tc_cat_05() {
        Category cat = Category.builder().name("Laptop").slug("laptop-tc05").active(true).build();
        Category saved = categoryService.create(cat);
        assertNotNull(saved, "Kết quả không được null");
        assertNotNull(saved.getId(), "ID phải được sinh ra");
        assertEquals("Laptop", saved.getName());
    }

    @Test
    @DisplayName("TC_CAT_06: update() – Cập nhật category tồn tại → name được cập nhật")
    public void tc_cat_06() {
        Category updated = categoryService.update(rootCatId,
            Category.builder().name("Root Updated").slug("root-updated-tc06").active(true).build());
        assertNotNull(updated, "Kết quả update không được null");
        assertEquals("Root Updated", updated.getName());
    }

    @Test
    @DisplayName("TC_CAT_07: update() – ID không tồn tại → trả về null")
    public void tc_cat_07() {
        Category result = categoryService.update(99999L,
            Category.builder().name("Ghost").slug("ghost-tc07").active(false).build());
        assertNull(result, "Phải trả về null khi category không tồn tại");
    }

    @Test
    @DisplayName("TC_CAT_08: delete() – Xóa category hợp lệ → getById trả về Optional.empty()")
    public void tc_cat_08() {
        // Tạo category mới để xóa (tránh ảnh hưởng fixture)
        Category cat = categoryRepository.save(
            Category.builder().name("ToDelete").slug("to-delete-tc08").active(true).build());
        Long id = cat.getId();

        categoryService.delete(id);
        assertTrue(categoryService.getById(id).isEmpty(), "Category đã bị xóa phải không còn trong DB");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP CAT-4: getAllCategoriesTree()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_CAT_09: getAllCategoriesTree() – Chỉ có root categories → success=true, data là list root")
    public void tc_cat_09() {
        ApiResponse resp = categoryService.getAllCategoriesTree();
        assertTrue(resp.isSuccess(), "Response phải success");
        assertNotNull(resp.getData(), "Data không được null");
    }

    @Test
    @DisplayName("TC_CAT_10: getAllCategoriesTree() – Có parent+child → root có children không rỗng")
    public void tc_cat_10() {
        // Tạo child của rootCatId
        Category child = categoryRepository.save(Category.builder()
            .name("Child")
            .slug("child-tc10")
            .active(true)
            .parent(categoryRepository.findById(rootCatId).orElseThrow())
            .build());

        ApiResponse resp = categoryService.getAllCategoriesTree();
        assertTrue(resp.isSuccess());

        @SuppressWarnings("unchecked")
        List<CategoryDTO> roots = (List<CategoryDTO>) resp.getData();
        // Tìm root tương ứng với rootCatId
        CategoryDTO rootDTO = roots.stream()
            .filter(r -> r.getId().equals(rootCatId))
            .findFirst()
            .orElse(null);

        assertNotNull(rootDTO, "Root category phải có trong kết quả cây");
        assertNotNull(rootDTO.getChildren(), "Children không được null");
        assertFalse(rootDTO.getChildren().isEmpty(), "Root phải có ít nhất 1 child");
    }

    @Test
    @DisplayName("TC_CAT_11: getAllCategoriesTree() – Sắp xếp displayOrder: order=1 xuất hiện trước order=2")
    public void tc_cat_11() {
        // Xóa fixture cũ, tạo 2 root có displayOrder rõ ràng
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        categoryRepository.save(Category.builder().name("Second").slug("second-tc11").active(true).displayOrder(2).build());
        categoryRepository.save(Category.builder().name("First").slug("first-tc11").active(true).displayOrder(1).build());

        ApiResponse resp = categoryService.getAllCategoriesTree();
        assertTrue(resp.isSuccess());

        @SuppressWarnings("unchecked")
        List<CategoryDTO> roots = (List<CategoryDTO>) resp.getData();
        assertTrue(roots.size() >= 2, "Phải có ít nhất 2 roots");
        Integer firstOrder = roots.get(0).getDisplayOrder();
        Integer secondOrder = roots.get(1).getDisplayOrder();
        assertTrue(firstOrder <= secondOrder, "Phần tử displayOrder nhỏ phải xuất hiện trước");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP CAT-5: getActiveCategories()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_CAT_12: getActiveCategories() – Có active và inactive → chỉ trả về 1 active")
    public void tc_cat_12() {
        // Xóa fixture cũ, tạo 1 active + 1 inactive root
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        categoryRepository.save(Category.builder().name("Active").slug("active-tc12").active(true).build());
        categoryRepository.save(Category.builder().name("Inactive").slug("inactive-tc12").active(false).build());

        ApiResponse resp = categoryService.getActiveCategories();
        assertTrue(resp.isSuccess());

        @SuppressWarnings("unchecked")
        List<CategoryDTO> actives = (List<CategoryDTO>) resp.getData();
        assertEquals(1, actives.size(), "Chỉ được trả về 1 category active");
        assertTrue(actives.get(0).getActive(), "Category trong danh sách phải active=true");
    }

    @Test
    @DisplayName("TC_CAT_13: getActiveCategories() – 1 root active + 1 child active → chỉ trả root, không trả child")
    public void tc_cat_13() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category root = categoryRepository.save(
            Category.builder().name("ActiveRoot").slug("active-root-tc13").active(true).build());
        categoryRepository.save(Category.builder()
            .name("ActiveChild").slug("active-child-tc13").active(true).parent(root).build());

        ApiResponse resp = categoryService.getActiveCategories();
        assertTrue(resp.isSuccess());

        @SuppressWarnings("unchecked")
        List<CategoryDTO> actives = (List<CategoryDTO>) resp.getData();
        assertEquals(1, actives.size(), "Chỉ được trả root, không trả child");
        assertEquals("ActiveRoot", actives.get(0).getName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP CAT-6: getCategoryWithProducts()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_CAT_14: getCategoryWithProducts() – ID hợp lệ → success=true, id khớp")
    public void tc_cat_14() {
        ApiResponse resp = categoryService.getCategoryWithProducts(rootCatId);
        assertTrue(resp.isSuccess(), "Response phải success");
        CategoryDTO dto = (CategoryDTO) resp.getData();
        assertNotNull(dto);
        assertEquals(rootCatId, dto.getId());
    }

    @Test
    @DisplayName("TC_CAT_15: getCategoryWithProducts() – ID không tồn tại → ném RuntimeException")
    public void tc_cat_15() {
        assertThrows(RuntimeException.class,
            () -> categoryService.getCategoryWithProducts(99999L),
            "Phải ném RuntimeException khi không tìm thấy category");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP CAT-7: createCategory()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_CAT_16: createCategory() – Slug hợp lệ không trùng → success=true, slug được lưu đúng")
    public void tc_cat_16() {
        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("Điện thoại").slug("dien-thoai-tc16").active(true).build();
        ApiResponse resp = categoryService.createCategory(req);
        assertTrue(resp.isSuccess(), "Tạo category với slug hợp lệ phải thành công");
        CategoryDTO dto = (CategoryDTO) resp.getData();
        assertEquals("dien-thoai-tc16", dto.getSlug());
    }

    @Test
    @DisplayName("TC_CAT_17: createCategory() – slug=null → tự sinh slug từ name (không null/rỗng)")
    public void tc_cat_17() {
        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("Điện thoại thông minh tc17").slug(null).active(true).build();
        ApiResponse resp = categoryService.createCategory(req);
        assertTrue(resp.isSuccess(), "Tạo category tự sinh slug phải thành công");
        CategoryDTO dto = (CategoryDTO) resp.getData();
        assertNotNull(dto.getSlug(), "Slug được tự tạo không được null");
        assertFalse(dto.getSlug().isEmpty(), "Slug được tự tạo không được rỗng");
    }

    @Test
    @DisplayName("TC_CAT_18: createCategory() – Slug đã tồn tại → success=false, message chứa 'Slug đã tồn tại'")
    public void tc_cat_18() {
        // Tạo slug trước
        categoryRepository.save(Category.builder().name("Existed").slug("existed-slug-tc18").active(true).build());

        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("New Cat").slug("existed-slug-tc18").active(true).build();
        ApiResponse resp = categoryService.createCategory(req);
        assertFalse(resp.isSuccess(), "Phải thất bại khi slug đã tồn tại");
        assertTrue(resp.getMessage().contains("Slug đã tồn tại"), "Message phải chứa 'Slug đã tồn tại'");
    }

    @Test
    @DisplayName("TC_CAT_19: createCategory() – Có parentId hợp lệ → success=true, DTO có parentId & parentName")
    public void tc_cat_19() {
        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("Child Cat tc19").slug("child-cat-tc19").active(true).parentId(rootCatId).build();
        ApiResponse resp = categoryService.createCategory(req);
        assertTrue(resp.isSuccess(), "Tạo category con phải thành công");
        CategoryDTO dto = (CategoryDTO) resp.getData();
        assertNotNull(dto.getParentId(), "DTO phải có parentId");
        assertEquals(rootCatId, dto.getParentId());
        assertNotNull(dto.getParentName(), "DTO phải có parentName");
    }

    @Test
    @DisplayName("TC_CAT_20: createCategory() – parentId không tồn tại → ném RuntimeException")
    public void tc_cat_20() {
        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("Orphan").slug("orphan-tc20").active(true).parentId(99999L).build();
        assertThrows(RuntimeException.class,
            () -> categoryService.createCategory(req),
            "Phải ném RuntimeException khi parentId không tồn tại");
    }

    @Test
    @DisplayName("TC_CAT_21: createCategory() – active=null → active=true (mặc định)")
    public void tc_cat_21() {
        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("Default Active tc21").slug("default-active-tc21").active(null).build();
        ApiResponse resp = categoryService.createCategory(req);
        assertTrue(resp.isSuccess(), "Tạo category phải thành công");
        CategoryDTO dto = (CategoryDTO) resp.getData();
        assertTrue(dto.getActive(), "active mặc định phải là true");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP CAT-8: updateCategory()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_CAT_22: updateCategory() – Cập nhật name & description → success=true, name được cập nhật")
    public void tc_cat_22() {
        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("Updated Name tc22").description("New Desc").slug("root-shared").active(true).build();
        ApiResponse resp = categoryService.updateCategory(rootCatId, req);
        assertTrue(resp.isSuccess(), "Cập nhật phải thành công");
        CategoryDTO dto = (CategoryDTO) resp.getData();
        assertEquals("Updated Name tc22", dto.getName());
    }

    @Test
    @DisplayName("TC_CAT_23: updateCategory() – Slug trùng với category khác → success=false, 'Slug đã tồn tại'")
    public void tc_cat_23() {
        // Tạo category thứ 2 với slug riêng
        Category other = categoryRepository.save(
            Category.builder().name("OtherCat").slug("other-slug-tc23").active(true).build());

        // Cập nhật rootCatId sang slug của other
        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("Root renamed").slug("other-slug-tc23").active(true).build();
        ApiResponse resp = categoryService.updateCategory(rootCatId, req);
        assertFalse(resp.isSuccess(), "Phải thất bại khi slug đã tồn tại ở category khác");
        assertTrue(resp.getMessage().contains("Slug đã tồn tại"));
    }

    @Test
    @DisplayName("TC_CAT_24: updateCategory() – ID không tồn tại → ném RuntimeException")
    public void tc_cat_24() {
        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("Ghost").slug("ghost-tc24").active(true).build();
        assertThrows(RuntimeException.class,
            () -> categoryService.updateCategory(99999L, req),
            "Phải ném RuntimeException khi category không tồn tại");
    }

    @Test
    @DisplayName("TC_CAT_25: BUG CHECK – Đặt category làm cha của chính nó → success=false, không thể là cha")
    public void tc_cat_25() {
        CreateCategoryRequest req = CreateCategoryRequest.builder()
            .name("Root").slug("root-shared").active(true).parentId(rootCatId).build();
        ApiResponse resp = categoryService.updateCategory(rootCatId, req);
        assertFalse(resp.isSuccess(),
            "BUG: Hệ thống đang cho phép category tự làm cha của chính nó!");
        assertTrue(resp.getMessage().contains("không thể là cha của chính nó"),
            "Message phải nêu rõ lý do không hợp lệ");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP CAT-9: toCategoryDTO()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_CAT_26: toCategoryDTO() – Category không có parent → DTO.parentId=null")
    public void tc_cat_26() {
        Category cat = categoryRepository.findById(rootCatId).orElseThrow();
        CategoryDTO dto = categoryService.toCategoryDTO(cat);
        assertNotNull(dto, "DTO không được null");
        assertEquals(rootCatId, dto.getId());
        assertEquals("Root", dto.getName());
        assertNull(dto.getParentId(), "parentId phải null khi không có parent");
    }

    @Test
    @DisplayName("TC_CAT_27: toCategoryDTO() – Category có parent → DTO.parentId = parent.id, parentName = parent.name")
    public void tc_cat_27() {
        Category parent = categoryRepository.findById(rootCatId).orElseThrow();
        Category child = categoryRepository.save(Category.builder()
            .name("ChildTC27").slug("child-tc27").active(true).parent(parent).build());

        // Flush rồi fetch lại để đảm bảo parent được load
        categoryRepository.flush();
        child = categoryRepository.findById(child.getId()).orElseThrow();

        CategoryDTO dto = categoryService.toCategoryDTO(child);
        assertEquals(rootCatId, dto.getParentId(), "parentId phải bằng ID của parent");
        assertEquals("Root", dto.getParentName(), "parentName phải đúng");
    }

    @Test
    @DisplayName("TC_CAT_28: toCategoryDTO() – Category có 2 children → DTO.children có 2 phần tử")
    public void tc_cat_28() {
        Category parent = categoryRepository.findById(rootCatId).orElseThrow();
        categoryRepository.save(Category.builder().name("C1").slug("c1-tc28").active(true).parent(parent).build());
        categoryRepository.save(Category.builder().name("C2").slug("c2-tc28").active(true).parent(parent).build());
        categoryRepository.flush();

        // Fetch lại parent để children được load
        parent = categoryRepository.findById(rootCatId).orElseThrow();
        CategoryDTO dto = categoryService.toCategoryDTO(parent);
        assertNotNull(dto.getChildren(), "children không được null");
        assertTrue(dto.getChildren().size() >= 2, "Phải có ít nhất 2 children");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ██████  ██████   ██████  ██████   ██    ██  ██████  ████████
    // ██      ██    ██ ██    ██ ██   ██  ██    ██ ██    ██    ██
    // ██████  ████████ ██    ██ ██   ██  ██    ██ ██         ██
    // ██      ██    ██ ██    ██ ██   ██  ██    ██ ██    ██   ██
    //  ██████  ██    ██  ██████ ██████    ██████   ██████    ██
    // ═══════════════════════════════════════════════════════════════════════════

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-1: getAll() & getById()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_01: getAll() – DB có dữ liệu → List có ít nhất 2 phần tử")
    public void tc_prod_01() {
        productRepository.save(Product.builder()
            .name("Product2").sku("SKU-P2").price(200.0).stockQuantity(5L).build());

        List<Product> list = productService.getAll();
        assertNotNull(list, "List không được null");
        assertTrue(list.size() >= 2, "Phải có ít nhất 2 sản phẩm");
    }

    @Test
    @DisplayName("TC_PROD_02: getAll() – DB rỗng → Trả về List rỗng")
    public void tc_prod_02() {
        productRepository.deleteAll();
        List<Product> list = productService.getAll();
        assertNotNull(list, "List không được null dù trống");
        assertTrue(list.isEmpty(), "Phải trả về list rỗng");
    }

    @Test
    @DisplayName("TC_PROD_03: getById() – ID hợp lệ → Optional.isPresent()=true, name đúng")
    public void tc_prod_03() {
        Optional<Product> result = productService.getById(prodId);
        assertTrue(result.isPresent(), "Phải tìm thấy sản phẩm");
        assertEquals("Demo Product", result.get().getName());
    }

    @Test
    @DisplayName("TC_PROD_04: getById() – ID không tồn tại → Optional.isEmpty()=true")
    public void tc_prod_04() {
        Optional<Product> result = productService.getById(99999L);
        assertTrue(result.isEmpty(), "Phải trả về Optional.empty() cho ID không tồn tại");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-2: create() & delete()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_05: create() – Sản phẩm hợp lệ → ID được sinh, name đúng")
    public void tc_prod_05() {
        Product input = Product.builder()
            .name("New Laptop").sku("SKU-NL-TC05").price(999.0).stockQuantity(5L).build();
        Product saved = productService.create(input);
        assertNotNull(saved, "Kết quả không được null");
        assertNotNull(saved.getId(), "ID phải được sinh");
        assertEquals("New Laptop", saved.getName());
    }

    @Test
    @DisplayName("TC_PROD_06: delete() – Xóa sản phẩm hợp lệ → getById trả Optional.empty()")
    public void tc_prod_06() {
        Product p = productRepository.save(
            Product.builder().name("ToDelete").sku("SKU-DEL-TC06").price(1.0).stockQuantity(1L).build());
        Long id = p.getId();

        productService.delete(id);
        assertTrue(productService.getById(id).isEmpty(), "Sản phẩm đã xóa không còn trong DB");
    }

    @Test
    @DisplayName("TC_PROD_07: create() – Không set active → active=true (mặc định @Builder.Default)")
    public void tc_prod_07() {
        Product input = Product.builder()
            .name("Default Active").sku("SKU-DA-TC07").price(50.0).stockQuantity(5L).build();
        Product saved = productService.create(input);
        assertTrue(saved.getActive(), "active mặc định phải là true");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-3: update()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_08: update() – Cập nhật name → name mới được lưu")
    public void tc_prod_08() {
        Product update = Product.builder().name("New Name TC08").build();
        Product result = productService.update(prodId, update);
        assertNotNull(result);
        assertEquals("New Name TC08", result.getName());
    }

    @Test
    @DisplayName("TC_PROD_09: update() – Cập nhật price=999.0 → price mới được lưu")
    public void tc_prod_09() {
        Product update = Product.builder().price(999.0).build();
        Product result = productService.update(prodId, update);
        assertNotNull(result);
        assertEquals(999.0, result.getPrice());
    }

    @Test
    @DisplayName("TC_PROD_10: update() – ID không tồn tại → trả về null")
    public void tc_prod_10() {
        Product update = Product.builder().name("Ghost").build();
        Product result = productService.update(99999L, update);
        assertNull(result, "Phải trả về null cho ID không tồn tại");
    }

    @Test
    @DisplayName("TC_PROD_11: update() – name=null → name cũ không bị xóa (null không ghi đè)")
    public void tc_prod_11() {
        String originalName = productService.getById(prodId).orElseThrow().getName();
        // Gửi product.name=null
        Product update = Product.builder().price(50.0).build(); // name không được set → null
        Product result = productService.update(prodId, update);
        assertNotNull(result);
        assertEquals(originalName, result.getName(), "Name cũ không được bị xóa khi truyền null");
    }

    @Test
    @DisplayName("TC_PROD_12: update() – Cập nhật category mới → product có category mới")
    public void tc_prod_12() {
        Category newCat = categoryRepository.save(
            Category.builder().name("NewCatTC12").slug("new-cat-tc12").active(true).build());

        Product update = Product.builder().category(newCat).build();
        Product result = productService.update(prodId, update);
        assertNotNull(result);
        assertNotNull(result.getCategory());
        assertEquals(newCat.getId(), result.getCategory().getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-4: toProductWithSpecs()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_13: toProductWithSpecs() – Product cơ bản → DTO có id, name, price, categoryId, categoryName đúng")
    public void tc_prod_13() {
        Product p = productService.getById(prodId).orElseThrow();
        ProductWithSpecsDTO dto = productService.toProductWithSpecs(p);
        assertNotNull(dto, "DTO không được null");
        assertEquals(prodId, dto.getId());
        assertEquals("Demo Product", dto.getName());
        assertEquals(100.0, dto.getPrice());
        assertEquals(rootCatId, dto.getCategoryId());
        assertEquals("Root", dto.getCategoryName());
    }

    @Test
    @DisplayName("TC_PROD_14: toProductWithSpecs() – Product không có category → categoryId=null, categoryName=null")
    public void tc_prod_14() {
        Product p = productRepository.save(Product.builder()
            .name("NoCatProduct").sku("SKU-NOCAT-TC14").price(10.0).stockQuantity(5L).build());
        ProductWithSpecsDTO dto = productService.toProductWithSpecs(p);
        assertNull(dto.getCategoryId(), "categoryId phải null khi không có category");
        assertNull(dto.getCategoryName(), "categoryName phải null khi không có category");
    }

    @Test
    @DisplayName("TC_PROD_15: toProductWithSpecs() – techSpecsJson hợp lệ → specifications có key 'RAM'='16GB'")
    public void tc_prod_15() {
        Product p = productRepository.save(Product.builder()
            .name("SpecProduct").sku("SKU-SPEC-TC15").price(500.0).stockQuantity(3L)
            .techSpecsJson("{\"RAM\":\"16GB\",\"ROM\":\"512GB\"}").build());
        ProductWithSpecsDTO dto = productService.toProductWithSpecs(p);
        assertNotNull(dto.getSpecifications(), "specifications không được null");
        assertEquals("16GB", dto.getSpecifications().get("RAM"));
    }

    @Test
    @DisplayName("TC_PROD_16: toProductWithSpecs() – techSpecsJson không hợp lệ → không throw exception, DTO vẫn trả về")
    public void tc_prod_16() {
        Product p = productRepository.save(Product.builder()
            .name("BadSpec").sku("SKU-BAD-TC16").price(100.0).stockQuantity(1L)
            .techSpecsJson("invalid-json").build());
        assertDoesNotThrow(() -> {
            ProductWithSpecsDTO dto = productService.toProductWithSpecs(p);
            assertNotNull(dto, "DTO vẫn phải trả về dù JSON lỗi");
        });
    }

    @Test
    @DisplayName("TC_PROD_17: toProductWithSpecs() – stock=10, reserved=3 → availableQuantity=7")
    public void tc_prod_17() {
        Product p = productRepository.save(Product.builder()
            .name("StockTest").sku("SKU-STOCK-TC17").price(100.0)
            .stockQuantity(10L).reservedQuantity(3L).build());
        ProductWithSpecsDTO dto = productService.toProductWithSpecs(p);
        assertEquals(7, dto.getAvailableQuantity(), "availableQuantity phải = stock - reserved = 7");
    }

    @Test
    @DisplayName("TC_PROD_18: toProductWithSpecs() – stock=2, reserved=5 → availableQuantity=0 (không âm)")
    public void tc_prod_18() {
        Product p = productRepository.save(Product.builder()
            .name("NegativeTest").sku("SKU-NEG-TC18").price(100.0)
            .stockQuantity(2L).reservedQuantity(5L).build());
        ProductWithSpecsDTO dto = productService.toProductWithSpecs(p);
        assertEquals(0, dto.getAvailableQuantity(), "availableQuantity không được âm");
        assertTrue(dto.getAvailableQuantity() >= 0, "availableQuantity phải >= 0");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-5: addProductImage() & getProductImages()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_19: addProductImage() – Thêm ảnh đầu tiên isPrimary=true → success=true, ảnh được lưu vào DB")
    public void tc_prod_19() {
        ApiResponse resp = productService.addProductImage(prodId, "http://img.test/1.jpg", true);
        assertTrue(resp.isSuccess(), "Thêm ảnh phải thành công");
        List<ProductImage> imgs = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId);
        assertFalse(imgs.isEmpty(), "Ảnh phải được lưu vào DB");
    }

    @Test
    @DisplayName("TC_PROD_20: addProductImage() – Ảnh đầu tiên, isPrimary=null → tự động set isPrimary=true")
    public void tc_prod_20() {
        productService.addProductImage(prodId, "http://img.test/auto.jpg", null);
        List<ProductImage> imgs = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId);
        assertFalse(imgs.isEmpty(), "Phải có ảnh trong DB");
        assertTrue(imgs.get(0).getIsPrimary(), "Ảnh đầu tiên phải tự động isPrimary=true");
    }

    @Test
    @DisplayName("TC_PROD_21: addProductImage() – Thêm ảnh primary thứ 2 → ảnh cũ isPrimary=false, ảnh mới isPrimary=true")
    public void tc_prod_21() {
        // Thêm ảnh primary đầu tiên
        productService.addProductImage(prodId, "http://img.test/old.jpg", true);
        ProductImage oldPrimary = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId).get(0);
        Long oldId = oldPrimary.getId();

        // Thêm ảnh primary thứ 2
        productService.addProductImage(prodId, "http://img.test/new.jpg", true);

        // Re-fetch ảnh cũ
        ProductImage reloaded = imageRepository.findById(oldId).orElseThrow();
        assertFalse(reloaded.getIsPrimary(), "Ảnh cũ phải mất isPrimary sau khi ảnh mới được set primary");

        // Ảnh mới phải isPrimary=true
        Optional<ProductImage> newPrimary = imageRepository.findByProductIdAndIsPrimaryTrue(prodId);
        assertTrue(newPrimary.isPresent(), "Phải có ảnh primary trong DB");
        assertEquals("http://img.test/new.jpg", newPrimary.get().getImageUrl());
    }

    @Test
    @DisplayName("TC_PROD_22: getProductImages() – productId có 2 ảnh → success=true, data list có 2 phần tử")
    public void tc_prod_22() {
        productService.addProductImage(prodId, "http://img.test/a.jpg", true);
        productService.addProductImage(prodId, "http://img.test/b.jpg", false);

        ApiResponse resp = productService.getProductImages(prodId);
        assertTrue(resp.isSuccess(), "Lấy ảnh phải success");

        @SuppressWarnings("unchecked")
        List<ProductImageDTO> images = (List<ProductImageDTO>) resp.getData();
        assertEquals(2, images.size(), "Phải có đúng 2 ảnh");
    }

    @Test
    @DisplayName("TC_PROD_23: addProductImage() – productId không tồn tại → ném RuntimeException")
    public void tc_prod_23() {
        assertThrows(RuntimeException.class,
            () -> productService.addProductImage(99999L, "http://img.test/err.jpg", true),
            "Phải ném RuntimeException khi product không tồn tại");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-6: setPrimaryImage() & deleteProductImage()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_24: setPrimaryImage() – imageId hợp lệ → success=true, ảnh đó isPrimary=true trong DB")
    public void tc_prod_24() {
        productService.addProductImage(prodId, "http://img.test/p1.jpg", false);
        productService.addProductImage(prodId, "http://img.test/p2.jpg", false);
        List<ProductImage> imgs = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId);
        Long targetId = imgs.get(1).getId();

        ApiResponse resp = productService.setPrimaryImage(prodId, targetId);
        assertTrue(resp.isSuccess(), "setPrimaryImage phải thành công");

        ProductImage target = imageRepository.findById(targetId).orElseThrow();
        assertTrue(target.getIsPrimary(), "Ảnh được chọn phải isPrimary=true");
    }

    @Test
    @DisplayName("TC_PROD_25: deleteProductImage() – Xóa ảnh thường (không phải primary) → success=true, ảnh bị xóa")
    public void tc_prod_25() {
        productService.addProductImage(prodId, "http://img.test/main.jpg", true);
        productService.addProductImage(prodId, "http://img.test/extra.jpg", false);

        List<ProductImage> imgs = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId);
        // Tìm ảnh không phải primary
        Long nonPrimaryId = imgs.stream()
            .filter(i -> !i.getIsPrimary()).findFirst().orElseThrow().getId();

        ApiResponse resp = productService.deleteProductImage(nonPrimaryId);
        assertTrue(resp.isSuccess(), "Xóa ảnh phải thành công");
        assertTrue(imageRepository.findById(nonPrimaryId).isEmpty(), "Ảnh đã xóa không còn trong DB");
    }

    @Test
    @DisplayName("TC_PROD_26: deleteProductImage() – Xóa ảnh primary khi còn ảnh khác → ảnh đầu tiên còn lại thành primary")
    public void tc_prod_26() {
        productService.addProductImage(prodId, "http://img.test/primary.jpg", true);
        productService.addProductImage(prodId, "http://img.test/second.jpg", false);

        Optional<ProductImage> primary = imageRepository.findByProductIdAndIsPrimaryTrue(prodId);
        assertTrue(primary.isPresent(), "Phải có ảnh primary trước khi xóa");
        Long primaryId = primary.get().getId();

        ApiResponse resp = productService.deleteProductImage(primaryId);
        assertTrue(resp.isSuccess(), "Xóa ảnh primary phải thành công");

        // Ảnh còn lại phải là primary
        Optional<ProductImage> newPrimary = imageRepository.findByProductIdAndIsPrimaryTrue(prodId);
        assertTrue(newPrimary.isPresent(), "Phải có ảnh primary mới sau khi xóa ảnh cũ");
        assertNotEquals(primaryId, newPrimary.get().getId(), "Primary mới phải khác ảnh cũ");
    }

    @Test
    @DisplayName("TC_PROD_27: deleteProductImage() – imageId không tồn tại → ném RuntimeException")
    public void tc_prod_27() {
        assertThrows(RuntimeException.class,
            () -> productService.deleteProductImage(99999L),
            "Phải ném RuntimeException khi ảnh không tồn tại");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-7: reorderProductImages() & updateProductImage()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_28: reorderProductImages() – Đảo thứ tự [id2, id1] → ảnh2 displayOrder=0, ảnh1 displayOrder=1")
    public void tc_prod_28() {
        productService.addProductImage(prodId, "http://img.test/r1.jpg", true);
        productService.addProductImage(prodId, "http://img.test/r2.jpg", false);

        List<ProductImage> imgs = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId);
        Long id1 = imgs.get(0).getId();
        Long id2 = imgs.get(1).getId();

        // Đảo thứ tự: [id2, id1]
        ApiResponse resp = productService.reorderProductImages(prodId, List.of(id2, id1));
        assertTrue(resp.isSuccess(), "Sắp xếp lại ảnh phải thành công");

        imageRepository.flush();
        ProductImage img2 = imageRepository.findById(id2).orElseThrow();
        ProductImage img1 = imageRepository.findById(id1).orElseThrow();
        assertEquals(0, img2.getDisplayOrder(), "Ảnh2 phải có displayOrder=0 sau khi đảo");
        assertEquals(1, img1.getDisplayOrder(), "Ảnh1 phải có displayOrder=1 sau khi đảo");
    }

    @Test
    @DisplayName("TC_PROD_29: updateProductImage() – imageUrl mới → success=true, imageUrl được cập nhật trong DB")
    public void tc_prod_29() {
        productService.addProductImage(prodId, "http://img.test/old-url.jpg", true);
        Long imageId = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId).get(0).getId();

        ProductImageDTO dto = ProductImageDTO.builder().imageUrl("http://img.test/new-url.jpg").build();
        ApiResponse resp = productService.updateProductImage(imageId, dto);
        assertTrue(resp.isSuccess(), "Cập nhật ảnh phải thành công");

        ProductImage updated = imageRepository.findById(imageId).orElseThrow();
        assertEquals("http://img.test/new-url.jpg", updated.getImageUrl());
    }

    @Test
    @DisplayName("TC_PROD_30: updateProductImage() – altText mới → success=true, altText='new alt' trong DB")
    public void tc_prod_30() {
        productService.addProductImage(prodId, "http://img.test/alt.jpg", true);
        Long imageId = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId).get(0).getId();

        ProductImageDTO dto = ProductImageDTO.builder().altText("new alt tc30").build();
        ApiResponse resp = productService.updateProductImage(imageId, dto);
        assertTrue(resp.isSuccess(), "Cập nhật altText phải thành công");

        ProductImage updated = imageRepository.findById(imageId).orElseThrow();
        assertEquals("new alt tc30", updated.getAltText());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-8: createProductFromWarehouse()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_31: createProductFromWarehouse() – Hợp lệ → success=true, Product được lưu với sku từ WP")
    public void tc_prod_31() {
        // Tạo WarehouseProduct mới (chưa liên kết Product)
        WarehouseProduct wp = warehouseProductRepository.save(WarehouseProduct.builder()
            .sku("WP-SKU-TC31").internalName("Warehouse Laptop").build());

        CreateProductFromWarehouseRequest req = CreateProductFromWarehouseRequest.builder()
            .warehouseProductId(wp.getId())
            .categoryId(rootCatId)
            .name("Published Laptop TC31")
            .price(1500.0)
            .build();

        ApiResponse resp = productService.createProductFromWarehouse(req);
        assertTrue(resp.isSuccess(), "Đăng bán từ kho phải thành công");
        // Kiểm tra product được lưu vào DB với sku từ WP
        boolean found = productRepository.findAll().stream()
            .anyMatch(p -> "WP-SKU-TC31".equals(p.getSku()));
        assertTrue(found, "Phải có product trong DB với sku từ WarehouseProduct");
    }

    @Test
    @DisplayName("TC_PROD_32: createProductFromWarehouse() – warehouseProductId không tồn tại → ném RuntimeException")
    public void tc_prod_32() {
        CreateProductFromWarehouseRequest req = CreateProductFromWarehouseRequest.builder()
            .warehouseProductId(99999L)
            .categoryId(rootCatId)
            .name("Ghost Product")
            .price(100.0)
            .build();
        assertThrows(RuntimeException.class,
            () -> productService.createProductFromWarehouse(req),
            "Phải ném RuntimeException khi WarehouseProduct không tồn tại");
    }

    @Test
    @DisplayName("TC_PROD_33: createProductFromWarehouse() – categoryId không tồn tại → ném RuntimeException")
    public void tc_prod_33() {
        WarehouseProduct wp = warehouseProductRepository.save(WarehouseProduct.builder()
            .sku("WP-SKU-TC33").internalName("WP TC33").build());

        CreateProductFromWarehouseRequest req = CreateProductFromWarehouseRequest.builder()
            .warehouseProductId(wp.getId())
            .categoryId(99999L)
            .name("NoCat Product")
            .price(100.0)
            .build();
        assertThrows(RuntimeException.class,
            () -> productService.createProductFromWarehouse(req),
            "Phải ném RuntimeException khi Category không tồn tại");
    }

    @Test
    @DisplayName("TC_PROD_34: BUG CHECK – createProductFromWarehouse() khi WP đã có Product → success=false, 'đã được đăng bán rồi'")
    public void tc_prod_34() {
        // Tạo WP rồi gọi createProductFromWarehouse thành công lần đầu
        WarehouseProduct wp = warehouseProductRepository.save(WarehouseProduct.builder()
            .sku("WP-SKU-TC34").internalName("WP TC34").build());

        CreateProductFromWarehouseRequest req = CreateProductFromWarehouseRequest.builder()
            .warehouseProductId(wp.getId())
            .categoryId(rootCatId)
            .name("First Publish").price(100.0).build();
        productService.createProductFromWarehouse(req);

        // Gọi lần thứ 2 với cùng WP → phải thất bại
        CreateProductFromWarehouseRequest req2 = CreateProductFromWarehouseRequest.builder()
            .warehouseProductId(wp.getId())
            .categoryId(rootCatId)
            .name("Second Publish").price(200.0).build();
        ApiResponse resp = productService.createProductFromWarehouse(req2);
        assertFalse(resp.isSuccess(),
            "BUG: Hệ thống phải ngăn đăng bán lại WarehouseProduct đã có Product!");
        assertTrue(resp.getMessage().contains("đã được đăng bán rồi"),
            "Message phải thông báo WP đã được đăng bán");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-9: updatePublishedProduct()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_35: updatePublishedProduct() – Cập nhật name và price → success=true, DB cập nhật đúng")
    public void tc_prod_35() {
        CreateProductFromWarehouseRequest req = CreateProductFromWarehouseRequest.builder()
            .name("Updated Name TC35")
            .price(9999.0)
            .build();
        ApiResponse resp = productService.updatePublishedProduct(prodId, req);
        assertTrue(resp.isSuccess(), "Cập nhật sản phẩm phải thành công");

        Product updated = productRepository.findById(prodId).orElseThrow();
        assertEquals("Updated Name TC35", updated.getName());
        assertEquals(9999.0, updated.getPrice());
    }

    @Test
    @DisplayName("TC_PROD_36: updatePublishedProduct() – Cập nhật category mới → category trong DB thay đổi")
    public void tc_prod_36() {
        Category newCat = categoryRepository.save(
            Category.builder().name("NewCatTC36").slug("new-cat-tc36").active(true).build());

        CreateProductFromWarehouseRequest req = CreateProductFromWarehouseRequest.builder()
            .name("Product TC36")
            .price(100.0)
            .categoryId(newCat.getId())
            .build();
        ApiResponse resp = productService.updatePublishedProduct(prodId, req);
        assertTrue(resp.isSuccess(), "Cập nhật category phải thành công");

        Product updated = productRepository.findById(prodId).orElseThrow();
        assertNotNull(updated.getCategory());
        assertEquals(newCat.getId(), updated.getCategory().getId());
    }

    @Test
    @DisplayName("TC_PROD_37: updatePublishedProduct() – productId không tồn tại → ném RuntimeException")
    public void tc_prod_37() {
        CreateProductFromWarehouseRequest req = CreateProductFromWarehouseRequest.builder()
            .name("Ghost").price(100.0).build();
        assertThrows(RuntimeException.class,
            () -> productService.updatePublishedProduct(99999L, req),
            "Phải ném RuntimeException khi product không tồn tại");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-10: unpublishProduct()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_38: unpublishProduct() – productId hợp lệ → success=true, Product bị xóa khỏi DB")
    public void tc_prod_38() {
        // Tạo product riêng để xóa (tránh orphan issue với prodId)
        Product toUnpublish = productRepository.save(Product.builder()
            .name("To Unpublish TC38").sku("SKU-UP-TC38").price(100.0).stockQuantity(5L).build());
        Long id = toUnpublish.getId();

        ApiResponse resp = productService.unpublishProduct(id);
        assertTrue(resp.isSuccess(), "Gỡ sản phẩm phải thành công");
        assertTrue(productRepository.findById(id).isEmpty(), "Product đã gỡ phải không còn trong DB");
    }

    @Test
    @DisplayName("TC_PROD_39: unpublishProduct() – productId không tồn tại → ném RuntimeException")
    public void tc_prod_39() {
        assertThrows(RuntimeException.class,
            () -> productService.unpublishProduct(99999L),
            "Phải ném RuntimeException khi product không tồn tại");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-11: update() branch coverage (full fields)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_40: update() – Cập nhật sku, stock, reserved, techSpecs → Lưu các trường đó")
    public void tc_prod_40() {
        Product update = Product.builder()
            .sku("SKU-UPDATE")
            .stockQuantity(100L)
            .reservedQuantity(10L)
            .techSpecsJson("{\"CPU\":\"i9\"}")
            .description("New desc")
            .build();
        Product result = productService.update(prodId, update);
        assertNotNull(result);
        assertEquals("SKU-UPDATE", result.getSku());
        assertEquals(100L, result.getStockQuantity());
        assertEquals(10L, result.getReservedQuantity());
        assertEquals("{\"CPU\":\"i9\"}", result.getTechSpecsJson());
        assertEquals("New desc", result.getDescription());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-12: publishProduct
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_41: publishProduct() – Hợp lệ → Lưu Product mới liên kết WarehouseProduct")
    public void tc_prod_41() {
        WarehouseProduct wp = warehouseProductRepository.save(WarehouseProduct.builder()
            .sku("WP-PUB-41").internalName("WP41").build());

        com.doan.WEB_TMDT.module.product.dto.PublishProductRequest req = 
            new com.doan.WEB_TMDT.module.product.dto.PublishProductRequest();
        req.setWarehouseProductId(wp.getId());
        req.setCategoryId(rootCatId);
        req.setName("Published Name");
        req.setPrice(1000.0);
        req.setDescription("Desc");

        Product saved = productService.publishProduct(req);
        assertNotNull(saved);
        assertEquals("Published Name", saved.getName());
        assertEquals(1000.0, saved.getPrice());
        assertNotNull(saved.getWarehouseProduct());
        assertEquals(wp.getId(), saved.getWarehouseProduct().getId());
        assertEquals(0L, saved.getStockQuantity());
    }

    @Test
    @DisplayName("TC_PROD_42: publishProduct() – WP không tồn tại → RuntimeException")
    public void tc_prod_42() {
        com.doan.WEB_TMDT.module.product.dto.PublishProductRequest req = 
            new com.doan.WEB_TMDT.module.product.dto.PublishProductRequest();
        req.setWarehouseProductId(99999L);
        assertThrows(RuntimeException.class, () -> productService.publishProduct(req));
    }

    @Test
    @DisplayName("TC_PROD_43: publishProduct() – Category không tồn tại → RuntimeException")
    public void tc_prod_43() {
        WarehouseProduct wp = warehouseProductRepository.save(WarehouseProduct.builder()
            .sku("WP-PUB-43").internalName("WP43").build());
        com.doan.WEB_TMDT.module.product.dto.PublishProductRequest req = 
            new com.doan.WEB_TMDT.module.product.dto.PublishProductRequest();
        req.setWarehouseProductId(wp.getId());
        req.setCategoryId(99999L);
        assertThrows(RuntimeException.class, () -> productService.publishProduct(req));
    }

    @Test
    @DisplayName("TC_PROD_44: publishProduct() – WP đã đăng bán → RuntimeException")
    public void tc_prod_44() {
        WarehouseProduct wp = warehouseProductRepository.save(WarehouseProduct.builder()
            .sku("WP-PUB-44").internalName("WP44").build());
        Product p = productRepository.save(Product.builder().name("Exist").sku("WP-PUB-44")
            .price(10.0).stockQuantity(1L).warehouseProduct(wp).build());

        WarehouseProduct wpRefreshed = warehouseProductRepository.findById(wp.getId()).orElseThrow();
        wpRefreshed.setProduct(p);
        warehouseProductRepository.save(wpRefreshed);

        com.doan.WEB_TMDT.module.product.dto.PublishProductRequest req = 
            new com.doan.WEB_TMDT.module.product.dto.PublishProductRequest();
        req.setWarehouseProductId(wpRefreshed.getId());
        req.setCategoryId(rootCatId);
        req.setName("Another");
        assertThrows(RuntimeException.class, () -> productService.publishProduct(req));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-13: getWarehouseProductsForPublish()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_45: getWarehouseProductsForPublish() – Có Supplier và isPublished")
    public void tc_prod_45() {
        // supplier = null
        WarehouseProduct wp1 = warehouseProductRepository.save(WarehouseProduct.builder()
            .sku("WP-LIST-45A").internalName("WP45A").supplier(null).build());
        
        inventoryStockRepository.save(InventoryStock.builder()
            .warehouseProduct(wp1).onHand(100L).damaged(20L).reserved(0L).build());

        Product p = productRepository.save(Product.builder().name("Linked").sku("WP-LIST-45A")
            .price(10.0).stockQuantity(1L).warehouseProduct(wp1).active(false).build());

        ApiResponse resp = productService.getWarehouseProductsForPublish();
        assertTrue(resp.isSuccess());
        @SuppressWarnings("unchecked")
        List<com.doan.WEB_TMDT.module.product.dto.WarehouseProductListResponse> list = 
            (List<com.doan.WEB_TMDT.module.product.dto.WarehouseProductListResponse>) resp.getData();

        com.doan.WEB_TMDT.module.product.dto.WarehouseProductListResponse item = list.stream()
            .filter(i -> "WP-LIST-45A".equals(i.getSku()))
            .findFirst().orElseThrow();
            
        assertEquals(80L, item.getSellableQuantity());
        assertTrue(item.getIsPublished());
        assertEquals(p.getId(), item.getPublishedProductId());
        assertFalse(item.getActive());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-14: toProductWithSpecs() via InventoryStock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_46: toProductWithSpecs() – Linked WP có InventoryStock → count availableQty = sellable")
    public void tc_prod_46() {
        WarehouseProduct wp = warehouseProductRepository.save(WarehouseProduct.builder()
            .sku("WP-SPEC-46").internalName("WP46").build());
        
        inventoryStockRepository.save(InventoryStock.builder()
            .warehouseProduct(wp).onHand(150L).reserved(20L).damaged(0L).build());

        Product p = productRepository.save(Product.builder().name("Linked WP")
            .sku("WP-SPEC-46").price(10.0).warehouseProduct(wp).build());

        ProductWithSpecsDTO dto = productService.toProductWithSpecs(p);
        assertEquals(130, dto.getAvailableQuantity());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-15: updateProductImage() full fields
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_47: updateProductImage() – Cập nhật isPrimary và displayOrder")
    public void tc_prod_47() {
        productService.addProductImage(prodId, "img1", false);
        productService.addProductImage(prodId, "img2", false);
        List<ProductImage> imgs = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId);
        Long id1 = imgs.get(0).getId();
        
        ProductImageDTO dto = ProductImageDTO.builder().isPrimary(true).displayOrder(5).build();
        ApiResponse resp = productService.updateProductImage(id1, dto);
        assertTrue(resp.isSuccess());

        ProductImage updated = imageRepository.findById(id1).orElseThrow();
        assertTrue(updated.getIsPrimary());
        assertEquals(5, updated.getDisplayOrder());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP PROD-16: Additional branch coverage (Null fields updates)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC_PROD_48: updatePublishedProduct() – Nhánh null (không update field nào) và branch getWarehouseProduct == null")
    public void tc_prod_48() {
        Product p = productRepository.save(Product.builder()
            .name("EmptyUpdate").sku("SKU-EMPTY").price(100.0).build()); // warehouseProduct = null

        com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest req = 
            com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest.builder().build(); // All null
        
        ApiResponse resp = productService.updatePublishedProduct(p.getId(), req);
        assertTrue(resp.isSuccess());
        Product updated = productRepository.findById(p.getId()).orElseThrow();
        assertEquals("EmptyUpdate", updated.getName(), "Name vẫn giữ nguyên do truyền null");
        assertEquals(100.0, updated.getPrice());
    }

    @Test
    @DisplayName("TC_PROD_49: updateProductImage() – Nhánh null (chỉ truyền ID, dto null các trường) -> bỏ qua update")
    public void tc_prod_49() {
        productService.addProductImage(prodId, "img-keep-49", true);
        Long id1 = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId).get(0).getId();

        ProductImageDTO dto = ProductImageDTO.builder().build(); // All fields null
        ApiResponse resp = productService.updateProductImage(id1, dto);
        assertTrue(resp.isSuccess());

        ProductImage updated = imageRepository.findById(id1).orElseThrow();
        assertEquals("img-keep-49", updated.getImageUrl());
        assertTrue(updated.getIsPrimary());
    }

    @Test
    @DisplayName("TC_PROD_50: createProductFromWarehouse() – warehouseProduct không có tồn kho (stockOpt is empty)")
    public void tc_prod_50() {
        WarehouseProduct wp = warehouseProductRepository.save(WarehouseProduct.builder()
            .sku("WP-NO-STOCK-50").internalName("WP50").build());

        com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest req = 
            com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest.builder()
            .warehouseProductId(wp.getId())
            .categoryId(rootCatId)
            .name("No Stock Product")
            .price(500.0)
            .build();
        
        ApiResponse resp = productService.createProductFromWarehouse(req);
        assertTrue(resp.isSuccess());
        Product saved = (Product) resp.getData();
        assertEquals(0L, saved.getStockQuantity(), "Stock phải = 0 khi không có InventoryStock");
    }

    @Test
    @DisplayName("TC_PROD_51: reorderProductImages() – Có imageId không tồn tại -> Bỏ qua, chạy tiếp ID hợp lệ")
    public void tc_prod_51() {
        productService.addProductImage(prodId, "img-51", true);
        Long id1 = imageRepository.findByProductIdOrderByDisplayOrderAsc(prodId).get(0).getId();

        ApiResponse resp = productService.reorderProductImages(prodId, List.of(99999L, id1));
        assertTrue(resp.isSuccess());

        ProductImage updated = imageRepository.findById(id1).orElseThrow();
        assertEquals(1, updated.getDisplayOrder(), "Ảnh hợp lệ vẫn được update displayOrder = 1");
    }
}
