using System.Windows;
using System.Windows.Controls;
using AgnesEditImage.Models;

namespace AgnesEditImage.Converters;

public class ChatItemTemplateSelector : DataTemplateSelector
{
    public DataTemplate? UserMessageTemplate { get; set; }
    public DataTemplate? ThoughtGroupTemplate { get; set; }
    public DataTemplate? AssistantTextTemplate { get; set; }
    public DataTemplate? PromptEnhancementTemplate { get; set; }
    public DataTemplate? StatusBannerTemplate { get; set; }
    public DataTemplate? ResultImageTemplate { get; set; }
    public DataTemplate? ErrorTemplate { get; set; }

    public override DataTemplate? SelectTemplate(object item, DependencyObject container)
    {
        if (item is UserMessage) return UserMessageTemplate;
        if (item is ThoughtGroup) return ThoughtGroupTemplate;
        if (item is AssistantText) return AssistantTextTemplate;
        if (item is PromptEnhancement) return PromptEnhancementTemplate;
        if (item is StatusBanner) return StatusBannerTemplate;
        if (item is ResultImage) return ResultImageTemplate;
        if (item is ErrorItem) return ErrorTemplate;
        return base.SelectTemplate(item, container);
    }
}
