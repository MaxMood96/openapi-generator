/**
 * OpenAPI Petstore
 * This is a sample server Petstore server. For this sample, you can use the api key `special-key` to test the authorization filters.
 *
 * The version of the OpenAPI document: 1.0.0
 *
 * NOTE: This class is auto generated by OpenAPI-Generator 7.15.0-SNAPSHOT.
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */



#include "CppRestPetstoreClient/model/Pet.h"

namespace org {
namespace openapitools {
namespace client {
namespace model {

Pet::Pet()
{
    m_Id = 0L;
    m_IdIsSet = false;
    m_CategoryIsSet = false;
    m_Name = utility::conversions::to_string_t("");
    m_NameIsSet = false;
    m_PhotoUrlsIsSet = false;
    m_TagsIsSet = false;
    m_StatusIsSet = false;
    m_MetadataIsSet = false;
}

Pet::~Pet()
{
}

void Pet::validate()
{
    // TODO: implement validation
}

web::json::value Pet::toJson() const
{
    web::json::value val = web::json::value::object();
    if(m_IdIsSet)
    {   
        
        val[utility::conversions::to_string_t(_XPLATSTR("id"))] = ModelBase::toJson(m_Id);
    }
    if(m_CategoryIsSet)
    {   
        
        val[utility::conversions::to_string_t(_XPLATSTR("category"))] = ModelBase::toJson(m_Category);
    }
    if(m_NameIsSet)
    {   
        
        val[utility::conversions::to_string_t(_XPLATSTR("name"))] = ModelBase::toJson(m_Name);
    }
    if(m_PhotoUrlsIsSet)
    {   
        
        val[utility::conversions::to_string_t(_XPLATSTR("photoUrls"))] = ModelBase::toJson(m_PhotoUrls);
    }
    if(m_TagsIsSet)
    {   
        
        val[utility::conversions::to_string_t(_XPLATSTR("tags"))] = ModelBase::toJson(m_Tags);
    }
    if(m_StatusIsSet)
    {   
        
        utility::string_t refVal = fromStatusEnum(m_Status);
        val[utility::conversions::to_string_t(_XPLATSTR("status"))] = ModelBase::toJson(refVal);
        
    }
    if(m_MetadataIsSet)
    {   
        
        val[utility::conversions::to_string_t(_XPLATSTR("metadata"))] = ModelBase::toJson(m_Metadata);
    }

    return val;
}

bool Pet::fromJson(const web::json::value& val)
{
    bool ok = true;
    if(val.has_field(utility::conversions::to_string_t(_XPLATSTR("id"))))
    {
        const web::json::value& fieldValue = val.at(utility::conversions::to_string_t(_XPLATSTR("id")));
        if(!fieldValue.is_null())
        {
            int64_t refVal_setId;
            ok &= ModelBase::fromJson(fieldValue, refVal_setId);
            setId(refVal_setId);
            
        }
    }
    if(val.has_field(utility::conversions::to_string_t(_XPLATSTR("category"))))
    {
        const web::json::value& fieldValue = val.at(utility::conversions::to_string_t(_XPLATSTR("category")));
        if(!fieldValue.is_null())
        {
            std::shared_ptr<Category> refVal_setCategory;
            ok &= ModelBase::fromJson(fieldValue, refVal_setCategory);
            setCategory(refVal_setCategory);
            
        }
    }
    if(val.has_field(utility::conversions::to_string_t(_XPLATSTR("name"))))
    {
        const web::json::value& fieldValue = val.at(utility::conversions::to_string_t(_XPLATSTR("name")));
        if(!fieldValue.is_null())
        {
            utility::string_t refVal_setName;
            ok &= ModelBase::fromJson(fieldValue, refVal_setName);
            setName(refVal_setName);
            
        }
    }
    if(val.has_field(utility::conversions::to_string_t(_XPLATSTR("photoUrls"))))
    {
        const web::json::value& fieldValue = val.at(utility::conversions::to_string_t(_XPLATSTR("photoUrls")));
        if(!fieldValue.is_null())
        {
            std::vector<utility::string_t> refVal_setPhotoUrls;
            ok &= ModelBase::fromJson(fieldValue, refVal_setPhotoUrls);
            setPhotoUrls(refVal_setPhotoUrls);
            
        }
    }
    if(val.has_field(utility::conversions::to_string_t(_XPLATSTR("tags"))))
    {
        const web::json::value& fieldValue = val.at(utility::conversions::to_string_t(_XPLATSTR("tags")));
        if(!fieldValue.is_null())
        {
            std::vector<std::shared_ptr<Tag>> refVal_setTags;
            ok &= ModelBase::fromJson(fieldValue, refVal_setTags);
            setTags(refVal_setTags);
            
        }
    }
    if(val.has_field(utility::conversions::to_string_t(_XPLATSTR("status"))))
    {
        const web::json::value& fieldValue = val.at(utility::conversions::to_string_t(_XPLATSTR("status")));
        if(!fieldValue.is_null())
        {
            utility::string_t refVal_setStatus;
            ok &= ModelBase::fromJson(fieldValue, refVal_setStatus);
            
            setStatus(toStatusEnum(refVal_setStatus));
            
        }
    }
    if(val.has_field(utility::conversions::to_string_t(_XPLATSTR("metadata"))))
    {
        const web::json::value& fieldValue = val.at(utility::conversions::to_string_t(_XPLATSTR("metadata")));
        if(!fieldValue.is_null())
        {
            std::shared_ptr<Object> refVal_setMetadata;
            ok &= ModelBase::fromJson(fieldValue, refVal_setMetadata);
            setMetadata(refVal_setMetadata);
            
        }
    }
    return ok;
}

void Pet::toMultipart(std::shared_ptr<MultipartFormData> multipart, const utility::string_t& prefix) const
{
    utility::string_t namePrefix = prefix;
    if(namePrefix.size() > 0 && namePrefix.substr(namePrefix.size() - 1) != utility::conversions::to_string_t(_XPLATSTR(".")))
    {
        namePrefix += utility::conversions::to_string_t(_XPLATSTR("."));
    }
    if(m_IdIsSet)
    {
        multipart->add(ModelBase::toHttpContent(namePrefix + utility::conversions::to_string_t(_XPLATSTR("id")), m_Id));
    }
    if(m_CategoryIsSet)
    {
        multipart->add(ModelBase::toHttpContent(namePrefix + utility::conversions::to_string_t(_XPLATSTR("category")), m_Category));
    }
    if(m_NameIsSet)
    {
        multipart->add(ModelBase::toHttpContent(namePrefix + utility::conversions::to_string_t(_XPLATSTR("name")), m_Name));
    }
    if(m_PhotoUrlsIsSet)
    {
        multipart->add(ModelBase::toHttpContent(namePrefix + utility::conversions::to_string_t(_XPLATSTR("photoUrls")), m_PhotoUrls));
    }
    if(m_TagsIsSet)
    {
        multipart->add(ModelBase::toHttpContent(namePrefix + utility::conversions::to_string_t(_XPLATSTR("tags")), m_Tags));
    }
    if(m_StatusIsSet)
    {
        multipart->add(ModelBase::toHttpContent(namePrefix + utility::conversions::to_string_t(_XPLATSTR("status")), fromStatusEnum(m_Status)));
    }
    if(m_MetadataIsSet)
    {
        multipart->add(ModelBase::toHttpContent(namePrefix + utility::conversions::to_string_t(_XPLATSTR("metadata")), m_Metadata));
    }
}

bool Pet::fromMultiPart(std::shared_ptr<MultipartFormData> multipart, const utility::string_t& prefix)
{
    bool ok = true;
    utility::string_t namePrefix = prefix;
    if(namePrefix.size() > 0 && namePrefix.substr(namePrefix.size() - 1) != utility::conversions::to_string_t(_XPLATSTR(".")))
    {
        namePrefix += utility::conversions::to_string_t(_XPLATSTR("."));
    }

    if(multipart->hasContent(utility::conversions::to_string_t(_XPLATSTR("id"))))
    {
        int64_t refVal_setId;
        ok &= ModelBase::fromHttpContent(multipart->getContent(utility::conversions::to_string_t(_XPLATSTR("id"))), refVal_setId );
        setId(refVal_setId);
    }
    if(multipart->hasContent(utility::conversions::to_string_t(_XPLATSTR("category"))))
    {
        std::shared_ptr<Category> refVal_setCategory;
        ok &= ModelBase::fromHttpContent(multipart->getContent(utility::conversions::to_string_t(_XPLATSTR("category"))), refVal_setCategory );
        setCategory(refVal_setCategory);
    }
    if(multipart->hasContent(utility::conversions::to_string_t(_XPLATSTR("name"))))
    {
        utility::string_t refVal_setName;
        ok &= ModelBase::fromHttpContent(multipart->getContent(utility::conversions::to_string_t(_XPLATSTR("name"))), refVal_setName );
        setName(refVal_setName);
    }
    if(multipart->hasContent(utility::conversions::to_string_t(_XPLATSTR("photoUrls"))))
    {
        std::vector<utility::string_t> refVal_setPhotoUrls;
        ok &= ModelBase::fromHttpContent(multipart->getContent(utility::conversions::to_string_t(_XPLATSTR("photoUrls"))), refVal_setPhotoUrls );
        setPhotoUrls(refVal_setPhotoUrls);
    }
    if(multipart->hasContent(utility::conversions::to_string_t(_XPLATSTR("tags"))))
    {
        std::vector<std::shared_ptr<Tag>> refVal_setTags;
        ok &= ModelBase::fromHttpContent(multipart->getContent(utility::conversions::to_string_t(_XPLATSTR("tags"))), refVal_setTags );
        setTags(refVal_setTags);
    }
    if(multipart->hasContent(utility::conversions::to_string_t(_XPLATSTR("status"))))
    {
        utility::string_t refVal_setStatus;
        ok &= ModelBase::fromHttpContent(multipart->getContent(utility::conversions::to_string_t(_XPLATSTR("status"))), refVal_setStatus );
        setStatus(toStatusEnum(refVal_setStatus));
    }
    if(multipart->hasContent(utility::conversions::to_string_t(_XPLATSTR("metadata"))))
    {
        std::shared_ptr<Object> refVal_setMetadata;
        ok &= ModelBase::fromHttpContent(multipart->getContent(utility::conversions::to_string_t(_XPLATSTR("metadata"))), refVal_setMetadata );
        setMetadata(refVal_setMetadata);
    }
    return ok;
}

Pet::StatusEnum Pet::toStatusEnum(const utility::string_t& value) const
{
    
    if (value == utility::conversions::to_string_t("available")) {
        return StatusEnum::AVAILABLE;
    }
    
    if (value == utility::conversions::to_string_t("pending")) {
        return StatusEnum::PENDING;
    }
    
    if (value == utility::conversions::to_string_t("sold")) {
        return StatusEnum::SOLD;
    }
    
    throw std::invalid_argument("Invalid value for conversion to StatusEnum");
}


const utility::string_t Pet::fromStatusEnum(const StatusEnum value) const
{
    switch(value)
    {
        
        case StatusEnum::AVAILABLE: return utility::conversions::to_string_t("available");
        
        case StatusEnum::PENDING: return utility::conversions::to_string_t("pending");
        
        case StatusEnum::SOLD: return utility::conversions::to_string_t("sold");
        
    }
}


int64_t Pet::getId() const
{
    return m_Id;
}

void Pet::setId(int64_t value)
{
    m_Id = value;
    m_IdIsSet = true;
}

bool Pet::idIsSet() const
{
    return m_IdIsSet;
}

void Pet::unsetId()
{
    m_IdIsSet = false;
}
std::shared_ptr<Category> Pet::getCategory() const
{
    return m_Category;
}


void Pet::setCategory(const std::shared_ptr<Category>& value)
{
    m_Category = value;
    m_CategoryIsSet = true;
}

bool Pet::categoryIsSet() const
{
    return m_CategoryIsSet;
}

void Pet::unsetCategory()
{
    m_CategoryIsSet = false;
}
utility::string_t Pet::getName() const
{
    return m_Name;
}


void Pet::setName(const utility::string_t& value)
{
    m_Name = value;
    m_NameIsSet = true;
}

bool Pet::nameIsSet() const
{
    return m_NameIsSet;
}

void Pet::unsetName()
{
    m_NameIsSet = false;
}
std::vector<utility::string_t> Pet::getPhotoUrls() const
{
    return m_PhotoUrls;
}


void Pet::setPhotoUrls(const std::vector<utility::string_t>& value)
{
    m_PhotoUrls = value;
    m_PhotoUrlsIsSet = true;
}

bool Pet::photoUrlsIsSet() const
{
    return m_PhotoUrlsIsSet;
}

void Pet::unsetPhotoUrls()
{
    m_PhotoUrlsIsSet = false;
}
std::vector<std::shared_ptr<Tag>> Pet::getTags() const
{
    return m_Tags;
}


void Pet::setTags(const std::vector<std::shared_ptr<Tag>>& value)
{
    m_Tags = value;
    m_TagsIsSet = true;
}

bool Pet::tagsIsSet() const
{
    return m_TagsIsSet;
}

void Pet::unsetTags()
{
    m_TagsIsSet = false;
}
Pet::StatusEnum Pet::getStatus() const
{
    return m_Status;
}


void Pet::setStatus(const StatusEnum value)
{
    m_Status = value;
    m_StatusIsSet = true;
}

bool Pet::statusIsSet() const
{
    return m_StatusIsSet;
}

void Pet::unsetStatus()
{
    m_StatusIsSet = false;
}
std::shared_ptr<Object> Pet::getMetadata() const
{
    return m_Metadata;
}


void Pet::setMetadata(const std::shared_ptr<Object>& value)
{
    m_Metadata = value;
    m_MetadataIsSet = true;
}

bool Pet::metadataIsSet() const
{
    return m_MetadataIsSet;
}

void Pet::unsetMetadata()
{
    m_MetadataIsSet = false;
}

}
}
}
}


