import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurConverseAgent } from '../../model/converse-agent.model';
import { NotifierService } from 'angular-notifier-updated';
import { TurConverseAgentService } from '../../service/converse-agent.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurLocale } from '../../../locale/model/locale.model';
import { TurLocaleService } from '../../../locale/service/locale.service';
import { TurSEInstance } from '../../../se/model/se-instance.model';
import { TurSEInstanceService } from '../../../se/service/se-instance.service';

@Component({
  selector: 'converse-agent-page',
  templateUrl: './converse-agent-page.component.html'
})
export class TurConverseAgentPageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turConverseAgent: Observable<TurConverseAgent>;
  private turLocales: Observable<TurLocale[]>;
  private turSEInstances: Observable<TurSEInstance[]>;
  private id: string;
  private newObject: boolean = false;

  constructor(private readonly notifier: NotifierService,
    private turConverseAgentService: TurConverseAgentService,
    private turLocaleService: TurLocaleService,
    private turSEInstanceService: TurSEInstanceService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.turLocales = turLocaleService.query();
    this.turSEInstances = turSEInstanceService.query();
    this.id= this.activatedRoute.snapshot.paramMap.get('id') || "";
    this.newObject = (this.id.toLowerCase() === 'new');
    this.turConverseAgent = this.newObject ? this.turConverseAgentService.getStructure() : this.turConverseAgentService.get(this.id);
  }

  getId(): string {
    return this.id;
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  ngOnInit(): void {
  }

  getTurConverseAgent(): Observable<TurConverseAgent> {
    return this.turConverseAgent;
  }

  public delete(_turConverseAgent: TurConverseAgent) {
    this.turConverseAgentService.delete(_turConverseAgent).subscribe(
      (turConverseAgent: TurConverseAgent) => {
        this.notifier.notify("success", _turConverseAgent.name.concat(" converse agent was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/console/converse/agent']);
      },
      response => {
        this.notifier.notify("error", "Converse agent was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
